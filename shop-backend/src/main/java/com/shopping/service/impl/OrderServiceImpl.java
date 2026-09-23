package com.shopping.service.impl;

import com.shopping.exception.ExceptionEnum;
import com.shopping.exception.XmException;
import com.shopping.mapper.OrderMapper;
import com.shopping.mapper.ProductMapper;
import com.shopping.mapper.SeckillProductMapper;
import com.shopping.mapper.ShoppingCartMapper;
import com.shopping.pojo.Order;
import com.shopping.pojo.Product;
import com.shopping.pojo.SeckillProduct;
import com.shopping.pojo.ShoppingCart;
import com.shopping.util.IdWorker;
import com.shopping.vo.CartVo;
import com.shopping.vo.OrderVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @Auther: wdd
 * @Date: 2020-03-19 13:21
 * @Description:
 */
@Service
public class OrderServiceImpl {

        private IdWorker idWorker;
        @Autowired
        private RedisTemplate redisTemplate;
        @Autowired
        private OrderMapper orderMapper;
        @Autowired
        private ShoppingCartMapper cartMapper;
        @Autowired
        private ProductMapper productMapper;
        @Autowired
        private SeckillProductMapper seckillProductMapper;

        private final static String SECKILL_PRODUCT_USER_LIST = "seckill:product:user:list";

        @Transactional
        public void addOrder(List<CartVo> cartVoList, Integer userId) {
                // 先添加订单
                String orderId = idWorker.nextId() + "";// 订单id
                long time = new Date().getTime();// 订单生成时间
                for (CartVo cartVo : cartVoList) {
                        Order order = new Order(null, orderId, userId, cartVo.getProductId(), cartVo.getNum(), cartVo.getPrice(), time);
                        try {
                                orderMapper.insert(order);
                        } catch (Exception e) {
                                throw new XmException(ExceptionEnum.ADD_ORDER_ERROR);
                        }

                        // 减去商品库存,记录卖出商品数量
                        // TODO : 此处会产生多线程问题，即不同用户同时对这个商品操作，此时会导致数量不一致问题,
                        /* Product product = productMapper.selectByPrimaryKey(cartVo.getProductId());
                           product.setProductNum(product.getProductNum() - cartVo.getNum());
                           product.setProductSales(product.getProductSales() + cartVo.getNum()); */
                        //所以利用数据库原子性+乐观锁+重试机制 通过产品数量>0 ,版本号是否相等来进行判断, 保证库存不会出现负数
                        // 获取当前商品信息
                        Product product = productMapper.selectById(cartVo.getProductId());
                        if (product == null) {
                                throw new RuntimeException("商品不存在");
                        }
                        // 尝试更新库存和版本号
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
                                }
                        }
                }

                // 删除购物车
                ShoppingCart cart = new ShoppingCart();
                cart.setUserId(userId);
                int count = cartMapper.deleteById(cart);
                if (count == 0) {
                        throw new XmException(ExceptionEnum.ADD_ORDER_ERROR);
                }
        }

        public List<List<OrderVo>> getOrder(Integer userId) {
                List<OrderVo> list = orderMapper.getOrderVoByUserId(userId);
                if (list.isEmpty()) {
                        throw new XmException(ExceptionEnum.GET_ORDER_NOT_FOUND);
                }

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

        @Transactional
        public void addSeckillOrder(String seckillId, String userId) {
                // 订单id
                String orderId = idWorker.nextId() + "";
                // 商品id
                SeckillProduct seckillProduct = seckillProductMapper.selectById(Integer.parseInt(seckillId));
                Integer productId = seckillProduct.getProductId();
                // 秒杀价格
                Double price = seckillProduct.getSeckillPrice();

                // 订单封装
                Order order = new Order(null, orderId, Integer.parseInt(userId), productId, 1, price, new Date().getTime());

                try {
                        orderMapper.insert(order);
                        // 减库存
                        seckillProductMapper.decrStock(seckillProduct.getSeckillId());
                } catch (Exception e) {
                        throw new XmException(ExceptionEnum.ADD_ORDER_ERROR);
                }

                // 订单创建成功, 将用户写入redis, 防止多次抢购
                redisTemplate.opsForList().leftPush(SECKILL_PRODUCT_USER_LIST + seckillId, userId);
        }

}
