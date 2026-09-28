import React, { useEffect, useState } from 'react';
import { Card, Col, Row, Table, Spin } from 'antd';
import {
  AreaChart, Area, BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer
} from 'recharts';
import api from '../api';

const STATUS = { 0: '待支付', 1: '已支付', 2: '已取消', 3: '已完成' };

export default function Dashboard() {
  const [data, setData] = useState(null);

  useEffect(() => {
    api.get('/admin/stats/overview', { params: { days: 7 } }).then(setData).catch(() => {});
  }, []);

  if (!data) {
    return <div className="app-page"><Spin size="large" style={{ marginTop: 120, width: '100%' }} /></div>;
  }

  const order = data.order || {};
  const user = data.user || {};
  const daily = (order.daily || []).map((d) => ({ ...d, gmv: Number(d.gmv) }));
  const growth = (data.userGrowth || []).map((d) => ({ ...d, newUsers: Number(d.newUsers) }));
  const top = (data.topSales || []).slice(0, 8).map((p, i) => ({
    key: p.productId, rank: i + 1, productName: p.productName,
    productSellingPrice: p.productSellingPrice, productSales: p.productSales, productNum: p.productNum
  }));
  const fmt = (n) => `¥${Number(n || 0).toLocaleString()}`;

  return (
    <div className="app-page">
      <Row gutter={[16, 16]}>
        <Col span={6}>
          <Card><div className="stat-card">
            <span className="label">总 GMV（已支付口径）</span>
            <span className="num">{fmt(order.totalGmv)}</span>
            <span className="sub">近 7 日曲线见下方</span>
          </div></Card>
        </Col>
        <Col span={6}>
          <Card><div className="stat-card">
            <span className="label">订单总数</span>
            <span className="num">{order.totalOrders ?? 0}</span>
            <span className="sub">已支付 {order.paidOrders ?? 0} · 待支付 {order.pendingOrders ?? 0}</span>
          </div></Card>
        </Col>
        <Col span={6}>
          <Card><div className="stat-card">
            <span className="label">注册用户</span>
            <span className="num">{user.total ?? 0}</span>
            <span className="sub">管理员 {user.admins ?? 0} 名</span>
          </div></Card>
        </Col>
        <Col span={6}>
          <Card><div className="stat-card">
            <span className="label">已取消订单</span>
            <span className="num warn">{order.cancelledOrders ?? 0}</span>
            <span className="sub">超时未支付自动取消</span>
          </div></Card>
        </Col>

        <Col span={14}>
          <Card title="近 7 日 GMV 曲线">
            <ResponsiveContainer width="100%" height={260}>
              <AreaChart data={daily}>
                <defs>
                  <linearGradient id="gmv" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="5%" stopColor="#5b6ef5" stopOpacity={0.5} />
                    <stop offset="95%" stopColor="#5b6ef5" stopOpacity={0.05} />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f5" />
                <XAxis dataKey="day" fontSize={12} />
                <YAxis fontSize={12} />
                <Tooltip formatter={(v) => fmt(v)} />
                <Area type="monotone" dataKey="gmv" stroke="#5b6ef5" fill="url(#gmv)" strokeWidth={2} />
              </AreaChart>
            </ResponsiveContainer>
          </Card>
        </Col>
        <Col span={10}>
          <Card title="每日新增注册">
            <ResponsiveContainer width="100%" height={260}>
              <BarChart data={growth}>
                <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f5" />
                <XAxis dataKey="day" fontSize={12} />
                <YAxis fontSize={12} allowDecimals={false} />
                <Tooltip />
                <Bar dataKey="newUsers" fill="#8f6ef5" radius={[6, 6, 0, 0]} />
              </BarChart>
            </ResponsiveContainer>
          </Card>
        </Col>

        <Col span={24}>
          <Card title="销量 Top 商品">
            <Table
              size="middle"
              columns={[
                { title: '#', dataIndex: 'rank', width: 60 },
                { title: '商品', dataIndex: 'productName' },
                { title: '售价', dataIndex: 'productSellingPrice', render: (v) => fmt(v), width: 120 },
                { title: '销量', dataIndex: 'productSales', width: 100, sorter: (a, b) => a.productSales - b.productSales },
                { title: '库存', dataIndex: 'productNum', width: 100,
                  render: (v) => <span style={{ color: v < 5 ? '#fa541c' : undefined }}>{v}</span> }
              ]}
              dataSource={top}
              pagination={false}
            />
          </Card>
        </Col>
      </Row>
    </div>
  );
}