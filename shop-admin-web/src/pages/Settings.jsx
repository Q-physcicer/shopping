import React from 'react';
import { Card, Button, message, Popconfirm, Descriptions, Alert } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import api, { currentUser } from '../api';

export default function Settings() {
  const rebuild = async () => {
    try {
      const res = await api.post('/admin/es/rebuild');
      // P1：后端 Result.msg 附带重建条数，优先展示（原 res 为 null，真实结果文案永不可见）
      message.success((res && res.__msg) || '索引重建已触发');
    } catch (e) {
      message.error(e.message);
    }
  };

  return (
    <div className="app-page">
      <Card title="系统设置">
        <Descriptions column={1} bordered style={{ marginBottom: 24 }}>
          <Descriptions.Item label="当前管理员">{currentUser()?.username}</Descriptions.Item>
          <Descriptions.Item label="网关地址">开发环境经本地 8080（SCG），生产经 Nginx /api 反代</Descriptions.Item>
          <Descriptions.Item label="AI 助手模型">DeepSeek Chat（网关鉴权后 Cookie 会话）</Descriptions.Item>
          <Descriptions.Item label="MCP Server">http://服务器:8106/sse（Claude Desktop 等外部客户端接入点）</Descriptions.Item>
        </Descriptions>

        <Alert
          style={{ marginBottom: 16 }}
          type="info"
          showIcon
          message="Elasticsearch 搜索说明"
          description="ES 开关在 shop-product 的 shop.search.es-enabled（环境变量 ES_ENABLED）。未开启或 ES 异常时自动降级 MySQL 模糊搜索，不影响可用性。装好 ES 后重建一次索引即可。"
        />

        <Popconfirm
          title="重建 ES 商品索引？"
          description="将从数据库全量拉取商品重新导入 product_index（需 ES 已启用）"
          onConfirm={rebuild}
        >
          <Button type="primary" icon={<ReloadOutlined />}>重建搜索索引</Button>
        </Popconfirm>
      </Card>
    </div>
  );
}