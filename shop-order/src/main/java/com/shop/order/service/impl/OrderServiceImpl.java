package com.shop.order.service.impl;

import com.shop.common.exception.ExceptionEnum;
import com.shop.common.exception.XmException;
import com.shop.order.mapper.OrderMapper;
import com.shop.order.mapper.ProductMapper;
import com.shop.order.mapper.SeckillProductMapper;
import com.shop.order.mapper.ShoppingCartMapper;
import com.shop.order.pojo.Order;
import com.shop.order.pojo.Product;
import com.shop.order.pojo.PaymentRecord;
import com.shop.order.pojo.SeckillProduct;
import com.shop.order.pojo.ShoppingCart;
import com.shop.order.pojo.UserMessage;
import com.shop.order.mapper.PaymentRecordMapper;
import com.shop.common.util.IdWorker;
import com.shop.order.vo.CartVo;
import com.shop.order.vo.OrderVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 订单服务（P2 高并发闭环）：
 * - 订单全部以 status=0(待支付) 落库，创建成功后发 TTL 消息；30 分钟未支付死信触发取消+库存回滚
 * - 秒杀订单由 MQ 消费者（SeckillOrderListener）异步落库
 *
 * @Auther: wdd
 */
@Service
public class OrderServiceImpl {

        private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

        /** 待支付订单 TTL：默认 30 分钟；演示/联调可用 -Dshop.order.pay-timeout-ms=15000 压缩 */
        @org.springframework.beans.factory.annotation.Value("${shop.order.pay-timeout-ms:1800000}")
        private long payTimeoutMs;

        /** 订单号生成器（修复历史隐患：原代码从未注入/初始化，调用 addOrder 即 NPE） */
        private final IdWorker idWorker = new IdWorker(1, 1);

        /** 支付单号生成器（workerId 与订单生成器错开，防同毫秒撞号） */
        private final IdWorker payIdWorker = new IdWorker(2, 2);

        @Autowired
        private OrderMapper orderMapper;
        @Autowired
        private ShoppingCartMapper cartMapper;
        @Autowired
        private ProductMapper productMapper;
        @Autowired
        private SeckillProductMapper seckillProductMapper;
        @Autowired
        private RabbitTemplate rabbitTemplate;
        @Autowired
        private com.shop.order.service.impl.UserMessageServiceImpl messageService;
        @Autowired
        private PaymentRecordMapper paymentMapper;
        @Autowired
        private com.shop.feign.UserClient userClient;

        /**
         * 购物车结算下单（P0-6 新契约入口）：地址经 Feign 回查 user 服务做快照。
         */
        @Transactional
        public String addOrderFromRequest(com.shop.feign.OrderCreateRequest request, Integer userId) {
                List<CartVo> cartVoList = new ArrayList<>();
                for (com.shop.feign.OrderCreateRequest.Item item : request.getItems()) {
                        CartVo vo = new CartVo();
                        vo.setProductId(item.getProductId());
                        vo.setNum(item.getNum());
                        cartVoList.add(vo);
                }
                // 地址快照：显式 addressId 指定，否则回落默认地址；均无则允许无地址下单（快照 null）
                String[] snapshot = resolveAddress(userId, request.getAddressId());
                String orderId = addOrder(cartVoList, userId);
                if (snapshot != null) {
                        applyAddressSnapshot(orderId, snapshot);
                }
                // 消息中心：下单成功通知（原缺陷：用户对下单无任何感知，仅取消有通知）
                messageService.push(userId, UserMessage.TYPE_ORDER_CREATED, "下单成功",
                                "订单 " + orderId + " 已提交，30 分钟内未支付将自动取消。");
                return orderId;
        }

        /** 经 Feign 回查收货地址（失败不阻塞下单：地址属增强信息，避免 user 瞬断卡死交易） */
        private String[] resolveAddress(Integer userId, Long addressId) {
                try {
                        com.shop.common.util.Result r;
                        if (addressId != null) {
                                r = userClient.getAddress(addressId);
                                if (r == null || r.getCode() == null || r.getCode() != 1 || r.getData() == null) {
                                        throw new XmException("收货地址不存在或不属于当前用户");
                                }
                        } else {
                                r = userClient.getDefaultAddress();
                                if (r == null || r.getCode() == null || r.getCode() != 1 || r.getData() == null) {
                                        return null;   // 无默认地址：不阻塞
                                }
                        }
                        Map<String, Object> m = toMap(r.getData());
                        StringBuilder full = new StringBuilder();
                        for (String k : new String[]{"province", "city", "district"}) {
                                String v = (String) m.get(k);
                                if (v != null && !v.isBlank()) {
                                        full.append(v.trim()).append(' ');
                                }
                        }
                        Object detail = m.get("detailAddress");
                        if (detail != null) {
                                full.append(String.valueOf(detail).trim());
                        }
                        return new String[]{(String) m.get("receiverName"), (String) m.get("receiverPhone"),
                                        full.toString().trim()};
                } catch (XmException xe) {
                        throw xe;   // 指定地址无效属业务错误：如实拒绝
                } catch (Exception e) {
                        log.warn("[Order] 地址回查失败（不阻塞下单）userId={}", userId, e);
                        return null;
                }
        }

        @SuppressWarnings("unchecked")
        private Map<String, Object> toMap(Object data) {
                // Feign 返回的 Result.data 已被 Jackson 反序列化为 Map
                if (data instanceof Map) {
                        return (Map<String, Object>) data;
                }
                throw new IllegalArgumentException("地址数据格式错误");
        }

        /** 地址快照写入订单全部行（订单号维度，INSERT 后统一补写） */
        private void applyAddressSnapshot(String orderId, String[] snapshot) {
                Order patch = new Order();
                patch.setReceiverName(snapshot[0]);
                patch.setReceiverPhone(snapshot[1]);
                patch.setReceiverAddress(snapshot[2]);
                orderMapper.update(patch, new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<Order>()
                                .eq(Order::getOrderId, orderId));
        }

        @Transactional
        public String addOrder(List<CartVo> cartVoList, Integer userId) {
                // 先添加订单
                String orderId = idWorker.nextId() + "";// 订单id
                long time = new Date().getTime();// 订单生成时间
                for (CartVo cartVo : cartVoList) {
                        // 数量钳制（防客户端传负数/超量：负数下单会反向增加库存）
                        if (cartVo.getNum() == null || cartVo.getNum() < 1 || cartVo.getNum() > 5) {
                                throw new XmException("购买数量须在 1-5 之间");
                        }

                        // 价格服务端回查：一律取商品表售价，绝不信任客户端传入的价格（对齐 placeDirectOrder）
                        Product product = productMapper.selectById(cartVo.getProductId());
                        if (product == null) {
                                throw new XmException("商品不存在或已下架");
                        }
                        Double unitPrice = product.getProductSellingPrice() != null
                                        ? product.getProductSellingPrice() : product.getProductPrice();
                        if (unitPrice == null || unitPrice <= 0) {
                                throw new XmException("商品价格异常，暂不可下单");
                        }

                        Order order = new Order(null, orderId, userId, cartVo.getProductId(), cartVo.getNum(), unitPrice, time);
                        order.setStatus(Order.STATUS_PENDING);
                        try {
                                orderMapper.insert(order);
                        } catch (Exception e) {
                                throw new XmException(ExceptionEnum.ADD_ORDER_ERROR);
                        }

                        // 减去商品库存,记录卖出商品数量
                        // 利用数据库原子性+乐观锁+重试机制 通过产品数量>0 ,版本号是否相等来进行判断, 保证库存不会出现负数
                        int retryTimes = 3;
                        boolean success = false;

                        while (retryTimes > 0 && !success) {
                                int updateCount = productMapper.updateStockByIdAndVersion(
                                                product.getProductId(),
                                                cartVo.getNum(),
                                                product.getVersion()
                                );
                                if (updateCount > 0) {
                                        success = true;
                                } else {
                                        retryTimes--;
                                        // 重新获取最新的 product 信息
                                        product = productMapper.selectById(product.getProductId());
                                        if (product == null) {
                                                break;   // 商品在重试间隙被删除：跳出后由兜底抛错回滚
                                        }
                                }
                        }
                        // 重试耗尽必须失败回滚（原缺陷：静默放行会卖出库存没扣的订单，即超卖）
                        if (!success) {
                                throw new XmException("库存不足或并发冲突，下单失败");
                        }
                }

                // 清空购物车中已下单的商品行（修复历史 bug：原 deleteById(cart) 传实体而非 id，恒删 0 行后误抛异常）
                if (!cartVoList.isEmpty()) {
                        List<Integer> productIds = cartVoList.stream()
                                        .map(CartVo::getProductId)
                                        .collect(Collectors.toList());
                        cartMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ShoppingCart>()
                                        .eq(ShoppingCart::getUserId, userId)
                                        .in(ShoppingCart::getProductId, productIds));
                }

                // 未支付超时取消
                sendPayTimeoutMessage(orderId);
                return orderId;
        }

        public List<List<OrderVo>> getOrder(Integer userId) {
                List<OrderVo> list = orderMapper.getOrderVoByUserId(userId);
                // P8: 无订单返回空列表——原抛 GET_ORDER_NOT_FOUND 会让 AI Agent 把
                // "无记录"当成"查询失败"，前端空态也由 UI 自行渲染

                // 分组并排序（保持原有逻辑）
                Map<String, List<OrderVo>> collect = list.stream()
                                .collect(Collectors.groupingBy(OrderVo::getOrderId));

                return collect.values().stream()
                                .sorted((list1, list2) -> {
                                        OrderVo order1 = list1.stream().max(Comparator.comparing(OrderVo::getOrderTime)).orElse(null);
                                        OrderVo order2 = list2.stream().max(Comparator.comparing(OrderVo::getOrderTime)).orElse(null);
                                        if (order1 == null && order2 == null) return 0;
                                        if (order1 == null) return 1;
                                        if (order2 == null) return -1;
                                        return order2.getOrderTime().compareTo(order1.getOrderTime());
                                })
                                .collect(Collectors.toList());
        }

        /**
         * Agent 直购下单（P4）：单商品直接落库，不动购物车。
         */
        @Transactional
        public String placeDirectOrder(Integer userId, Integer productId, Integer num) {
                String orderId = idWorker.nextId() + "";
                long time = new Date().getTime();
                // 校验商品存在 + 库存
                Product product = productMapper.selectById(productId);
                if (product == null) {
                        throw new XmException(ExceptionEnum.GET_PRODUCT_NOT_FOUND);
                }
                if (product.getProductNum() == null || product.getProductNum() < num) {
                        throw new XmException(ExceptionEnum.ADD_ORDER_ERROR);
                }
                Order order = new Order(null, orderId, userId, productId, num,
                                product.getProductSellingPrice(), time);
                order.setStatus(Order.STATUS_PENDING);
                orderMapper.insert(order);

                // 乐观锁扣库存（同 addOrder）
                int retryTimes = 3;
                boolean success = false;
                while (retryTimes > 0 && !success) {
                        int updateCount = productMapper.updateStockByIdAndVersion(
                                        product.getProductId(), num, product.getVersion());
                        if (updateCount > 0) {
                                success = true;
                        } else {
                                retryTimes--;
                                product = productMapper.selectById(productId);
                                if (product == null || product.getProductNum() < num) {
                                        throw new XmException(ExceptionEnum.ADD_ORDER_ERROR);
                                }
                        }
                }
                if (!success) {
                        throw new XmException(ExceptionEnum.ADD_ORDER_ERROR);
                }

                // P0-6：Agent 直购无显式地址，自动回落用户默认地址（拉不到不阻塞）
                String[] snapshot = resolveAddress(userId, null);
                if (snapshot != null) {
                        applyAddressSnapshot(orderId, snapshot);
                }

                // 消息中心：下单成功通知
                messageService.push(userId, UserMessage.TYPE_ORDER_CREATED, "下单成功",
                                "订单 " + orderId + " 已提交（AI 客服代购），30 分钟内未支付将自动取消。");

                sendPayTimeoutMessage(orderId);
                log.info("[DirectOrder] Agent 直购订单已生成 orderId={} userId={} productId={}x{}", orderId, userId, productId, num);
                return orderId;
        }

        /**
         * 秒杀订单落库（由 SeckillOrderListener 在幂等 CAS 成功后调用）。
         * 防重复抢购已由 product 端 Redis SADD 保证，本方法不再写 Redis 防重集合。
         */
        @Transactional
        public String addSeckillOrder(String seckillId, String userId) {
                // 订单id
                String orderId = idWorker.nextId() + "";
                SeckillProduct seckillProduct = seckillProductMapper.selectById(Integer.parseInt(seckillId));
                Integer productId = seckillProduct.getProductId();
                Double price = seckillProduct.getSeckillPrice();

                try {
                        Order order = new Order(null, orderId, Integer.parseInt(userId), productId, 1, price, new Date().getTime());
                        order.setStatus(Order.STATUS_PENDING);
                        order.setSeckillId(seckillProduct.getSeckillId());
                        orderMapper.insert(order);
                        // 减库存（DB 可售库存，Redis 预扣是入口层的快速判断）
                        seckillProductMapper.decrStock(seckillProduct.getSeckillId());
                } catch (Exception e) {
                        log.error("[SecKillOrder] 落库失败 seckillId={} userId={}", seckillId, userId, e);
                        throw new XmException(ExceptionEnum.ADD_ORDER_ERROR);
                }

                sendPayTimeoutMessage(orderId);
                return orderId;
        }

        /**
         * 发送待支付订单的 TTL 消息（30 分钟）：
         * order.pay.timeout.queue 无消费者、带死信路由 → 到期死信进 order.pay.cancel.queue → OrderTimeoutListener 取消订单
         */
        public void sendPayTimeoutMessage(String orderId) {
                rabbitTemplate.convertAndSend("order.pay.timeout.queue", (Object) orderId, new MessagePostProcessor() {
                        @Override
                        public org.springframework.amqp.core.Message postProcessMessage(
                                        org.springframework.amqp.core.Message message) {
                                message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
                                message.getMessageProperties().setExpiration(String.valueOf(payTimeoutMs));
                                return message;
                        }
                });
        }

        /**
         * 模拟支付（P0 修复：三连写事务化）。
         * payIfPending CAS → payment_record 流水 → 站内信 三步同库，必须同事务：
         * 原实现中途失败会出现"订单已置已支付但无流水无消息"，且客户端重试得到"不可支付"。
         */
        @Transactional
        public Map<String, Object> payMock(Integer userId, String orderId) {
                List<Order> rows = orderMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                                                .eq(Order::getOrderId, orderId).eq(Order::getUserId, userId));
                if (rows.isEmpty()) {
                        throw new XmException(ExceptionEnum.GET_ORDER_NOT_FOUND);
                }
                double amount = rows.stream().mapToDouble(o -> o.getProductPrice() * o.getProductNum()).sum();

                // CAS：与超时取消互斥，只赢一个
                int updated = orderMapper.payIfPending(orderId, System.currentTimeMillis());
                if (updated == 0) {
                        throw new XmException("订单不可支付（可能已支付/已取消）");
                }

                // 流水
                PaymentRecord record = new PaymentRecord();
                record.setPayNo("MOCK" + payIdWorker.nextId());
                record.setOrderId(orderId);
                record.setUserId(userId);
                record.setAmount(amount);
                record.setPayChannel("MOCK");
                record.setStatus(1);
                record.setCreatedAt(new Date());
                paymentMapper.insert(record);

                // 消息中心：支付成功
                messageService.push(userId, UserMessage.TYPE_ORDER_PAID, "支付成功",
                                "订单 " + orderId + " 支付成功，金额 ￥" + amount + "。可在「我的订单」查看详情。");

                log.info("[Pay] 模拟支付成功 orderId={} userId={} amount={}", orderId, userId, amount);
                Map<String, Object> data = new HashMap<>();
                data.put("payNo", record.getPayNo());
                data.put("amount", amount);
                return data;
        }

        /** 超时取消 + 库存回滚（由 OrderTimeoutListener 调用）；返回是否取消成功 */
        public boolean cancelOrderIfTimeout(String orderId) {
                int updated = orderMapper.cancelIfPending(orderId);
                if (updated == 0) {
                        return false;   // 已支付或已处理
                }
                // 该订单号下的全部行（一个订单号可能含多个商品行）回滚库存
                List<Order> rows = orderMapper.selectList(
                                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                                                .eq(Order::getOrderId, orderId));
                if (!rows.isEmpty()) {
                        // 消息中心：超时未支付已取消（同一订单号归属同一用户，取首行 userId）
                        messageService.push(rows.get(0).getUserId(), UserMessage.TYPE_ORDER_CANCELLED, "订单已取消",
                                        "订单 " + orderId + " 因超时未支付已自动取消，商品库存已回滚。");
                }
                for (Order row : rows) {
                        if (row.getSeckillId() != null) {
                                try {
                                        seckillProductMapper.incrStock(row.getSeckillId());
                                } catch (Exception e) {
                                        log.error("[TimeoutCancel] 秒杀库存回滚失败 seckillId={}", row.getSeckillId(), e);
                                }
                        } else {
                                try {
                                        productMapper.rollbackStock(row.getProductId(), row.getProductNum());
                                } catch (Exception e) {
                                        log.error("[TimeoutCancel] 商品库存回滚失败 productId={}", row.getProductId(), e);
                                }
                        }
                }
                log.info("[TimeoutCancel] 订单超时未支付已取消并回滚库存 orderId={}", orderId);
                return true;
        }
}