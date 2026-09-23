package com.shopping.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.shopping.mapper.ProductMapper;
import com.shopping.mapper.SeckillProductMapper;
import com.shopping.mapper.SeckillTimeMapper;
import com.shopping.pojo.SeckillProduct;
import com.shopping.pojo.SeckillTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class SeckillTask {

    @Autowired
    private SeckillTimeMapper seckillTimeMapper;
    @Autowired
    private ProductMapper productMapper;
    @Autowired
    private SeckillProductMapper seckillProductMapper;

    @Scheduled(cron = "0 0 15 * * ?")
    public void execute() {
        // 获取商品ID列表
        List<Integer> productIds = productMapper.selectIds();
        Date time = getDate();

        // 清空旧数据（使用MyBatis-Plus的delete方法）
        seckillTimeMapper.delete(new LambdaQueryWrapper<>());
        seckillProductMapper.delete(new LambdaQueryWrapper<>());

        for (int i = 1; i < 24; i += 2) {
            // 插入时间段
            long startTime = time.getTime() / 1000 * 1000 + 1000 * 60 * 60 * i;
            long endTime = startTime + 1000 * 60 * 60;

            SeckillTime seckillTime = new SeckillTime();
            seckillTime.setStartTime(startTime);
            seckillTime.setEndTime(endTime);
            seckillTimeMapper.insert(seckillTime);

            // 随机选择15个商品
            Set<Integer> randomProductIds = new HashSet<>();
            Random random = new Random();
            while (randomProductIds.size() < 15 && !productIds.isEmpty()) {
                int index = random.nextInt(productIds.size());
                randomProductIds.add(productIds.get(index));
            }
            List<Integer> selectedProductIds = new ArrayList<>(randomProductIds);

            // 批量添加秒杀商品
            List<SeckillProduct> seckillProducts = new ArrayList<>();
            for (Integer productId : selectedProductIds) {
                SeckillProduct seckillProduct = new SeckillProduct();
                seckillProduct.setSeckillPrice(1000.0);
                seckillProduct.setSeckillStock(100);
                seckillProduct.setProductId(productId);
                seckillProduct.setTimeId(seckillTime.getTimeId());
                seckillProducts.add(seckillProduct);
            }

            seckillProducts.forEach(seckillProductMapper::insert);

            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                e.printStackTrace();
            }

            System.out.println("完成时间段 " + i + " 的秒杀商品配置");
        }

        System.out.println("一次添加ok-------------------------------------------");
    }

    private Date getDate() {
        Calendar ca = Calendar.getInstance();
        ca.set(Calendar.MINUTE, 0);
        ca.set(Calendar.SECOND, 0);
        return ca.getTime();
    }
}
