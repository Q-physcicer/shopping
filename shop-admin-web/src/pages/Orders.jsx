import React, { useEffect, useState } from 'react';
import { Card, Table, Select, Space, Tag, message } from 'antd';
import api from '../api';

const STATUS = {
  0: { text: '待支付', color: 'orange' },
  1: { text: '已支付', color: 'green' },
  2: { text: '已取消', color: 'default' },
  3: { text: '已完成', color: 'blue' }
};

const fmtTime = (ms) => {
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

export default function Orders() {
  const [rows, setRows] = useState([]);
  const [status, setStatus] = useState(null);
  const [page, setPage] = useState(1);
  const [total, setTotal] = useState(0);

  const load = () => {
    api.get('/admin/order/page', { params: { page, size: 10, status: status ?? undefined } })
      .then((d) => { setRows(d || []); setTotal((d || []).length >= 10 ? page * 10 + 1 : (page - 1) * 10 + (d || []).length); })
      .catch((e) => message.error(e.message));
  };

  useEffect(() => { load(); }, [page, status]);

  return (
    <div className="app-page">
      <Card
        title="订单管理"
        extra={
          <Select allowClear placeholder="全部状态" style={{ width: 140 }} value={status}
            onChange={(v) => { setStatus(v); setPage(1); }}
            options={Object.entries(STATUS).map(([k, v]) => ({ value: Number(k), label: v.text }))} />
        }
      >
        <Table
          rowKey="id"
          dataSource={rows}
          pagination={{ current: page, total, pageSize: 10, onChange: setPage }}
          columns={[
            { title: '订单号', dataIndex: 'orderId', width: 190, render: (v) => v || '-' },
            { title: '用户ID', dataIndex: 'userId', width: 80 },
            { title: '商品ID', dataIndex: 'productId', width: 80 },
            { title: '数量', dataIndex: 'productNum', width: 60 },
            { title: '金额', dataIndex: 'productPrice', width: 100, render: (v) => `¥${v}` },
            { title: '下单时间', width: 150, render: (_, r) => fmtTime(r.orderTime) },
            {
              title: '状态', width: 100,
              render: (_, r) => {
                const s = STATUS[r.status] || { text: r.status, color: 'default' };
                return <Tag color={s.color}>{s.text}</Tag>;
              }
            },
            { title: '秒杀单', dataIndex: 'seckillId', width: 90, render: (v) => (v ? <Tag color="red">秒杀 #{v}</Tag> : '-') }
          ]}
        />
      </Card>
    </div>
  );
}