package com.shopping.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring AI 聊天客户端配置
 * 云端模型通过 OpenAI 兼容协议接入（DeepSeek），配置见 application.yml 的 spring.ai.openai
 */
@Configuration
public class AiConfig {

    @Bean
    public ChatClient chatClient(org.springframework.ai.chat.model.ChatModel chatModel) {
        String systemPrompt = """
                你是「星选商城」购物商场的智能客服助理，名叫小智。你的职责：
                1. 介绍商场在售商品（手机、电视机、空调等数码家电）、促销活动、秒杀信息；
                2. 解答下单流程、购物车使用、支付方式、物流配送、退换货及售后政策等问题；
                3. 回答要简洁、热情、专业，使用中文，适当使用表情符号提升亲和力；
                4. 如果用户询问与购物无关的话题，礼貌说明你只负责商场客服咨询，并引导回购物话题；
                5. 涉及具体价格、库存时，提醒用户以商品详情页实际展示为准，不要编造不确定的商品信息。
                """;
        return ChatClient.builder(chatModel)
                .defaultSystem(systemPrompt)
                .build();
    }
}