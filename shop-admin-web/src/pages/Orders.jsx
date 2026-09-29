import React, { useEffect, useState } from 'react';
import { Card, Table, Select, Space, Tag, Input, Modal, message } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import api from '../api';

const STATUS = {
  0: { text: '待支付', color: 'orange' },
  1: { text: '已支付', color: 'green' },
  2: { text: '已取消', color: 'default' },
  3: { text: '已完成', color: 'blue' }
};

const fmtTime = (ms) => {
  if (!ms) return '-';
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

export default function Orders() {
  const [rows, setRows] = useState([]);
  const [status, setStatus] = useState(null);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [orderIdKw, setOrderIdKw] = useState('');
  const [orderSearch, setOrderSearch] = useState('');

  // P1：用后端真 total（原 length>=10 猜测会出现幽灵下一页）；支持订单号搜索
  const load = () => {
    setLoading(true);
    api.get('/admin/order/page', {
      params: { page, size: 10, status: status ?? undefined, orderId: orderSearch || undefined }
    })
      .then((d) => { setRows(d.list || []); setTotal(d.total || 0); })
      .catch((e) => message.error(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [page, status, orderSearch]);

  const markDone = (record) => {
    Modal.confirm({
      title: `标记订单完成？`,
      content: `订单 ${record.orderId}（¥${record.productPrice * record.productNum}）将置为已完成`,
      onOk: async () => {
        try {
          const res = await api.post('/admin/order/done', { orderId: record.orderId });
          message.success((res && res.__msg) || '已标记完成');
          load();
        } catch (e) { message.error(e.message); }
      }
    });
  };

  return (
    <div className="app-page">
      <Card
        title="订单管理"
        extra={
          <Space>
            <Input allowClear prefix={<SearchOutlined />} placeholder="订单号搜索（回车）" style={{ width: 220 }}
              value={orderIdKw} onChange={(e) => setOrderIdKw(e.target.value)}
              onPressEnter={() => { setPage(1); setOrderSearch(orderIdKw.trim()); }} />
            <Select allowClear placeholder="全部状态" style={{ width: 130 }} value={status}
              onChange={(v) => { setStatus(v); setPage(1); }}
              options={Object.entries(STATUS).map(([k, v]) => ({ value: Number(k), label: v.text }))} />
          </Space>
        }
      >
        <Table
          rowKey="id"
          loading={loading}
          dataSource={rows}
          pagination={{ current: page, total, pageSize: 10, onChange: setPage, showTotal: (t) => `共 ${t} 条` }}
          columns={[
            { title: '订单号', dataIndex: 'orderId', width: 190, render: (v) => v || '-' },
            { title: '用户ID', dataIndex: 'userId', width: 80 },
            { title: '商品ID', dataIndex: 'productId', width: 80 },
            { title: '数量', dataIndex: 'productNum', width: 60 },
            { title: '金额', dataIndex: 'productPrice', width: 100, render: (v) => `¥${v}` },
            { title: '收货人', dataIndex: 'receiverName', width: 90, render: (v) => v || '-' },
            { title: '下单时间', width: 150, render: (_, r) => fmtTime(r.orderTime) },
            {
              title: '状态', width: 100,
              render: (_, r) => {
                const s = STATUS[r.status] || { text: r.status, color: 'default' };
                return <Tag color={s.color}>{s.text}</Tag>;
              }
            },
            { title: '秒杀单', dataIndex: 'seckillId', width: 90, render: (v) => (v ? <Tag color="red">秒杀 #{v}</Tag> : '-') },
            {
              // P1：状态 3（已完成）从此可达——原无任何代码路径能设置
              title: '操作', width: 100,
              render: (_, r) => (r.status === 1
                ? <a onClick={() => markDone(r)}>标记完成</a>
                : '-')
            }
          ]}
        />
      </Card>
    </div>
  );
}
