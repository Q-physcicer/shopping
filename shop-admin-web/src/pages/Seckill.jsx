import React, { useEffect, useState } from 'react';
import { Card, Table, Button, Modal, Form, InputNumber, Select, message, Tag } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import api from '../api';

const fmtTime = (ms) => {
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getMonth() + 1}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

export default function Seckill() {
  const [times, setTimes] = useState([]);
  const [rows, setRows] = useState([]);
  const [categories, setCategories] = useState([]);
  const [products, setProducts] = useState([]);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  // 管理端按场次过滤列表：复用公开放到 product 服务的秒杀浏览接口太散——直接读 admin 的场次与商品分页
  const load = () => {
    api.get('/admin/seckill/time').then((t) => {
      // 展示未来即将开始的场次（附带每个场次一个占位创建入口）
      setTimes(t || []);
    }).catch(() => {});
    // 商品选择框数据（搜索已有商品）
    api.get('/admin/product/page', { params: { page: 1, size: 100 } }).then((d) => {
      setProducts((d.list || []).map((p) => ({ value: p.productId, label: `#${p.productId} ${p.productName}` })));
    }).catch(() => {});
  };

  useEffect(() => { load(); }, []);

  const submit = async () => {
    const v = await form.validateFields();
    try {
      const res = await api.post('/admin/seckill', v);
      message.success(res || '秒杀商品已添加');
      setOpen(false);
      load();
    } catch (e) {
      message.error(e.message);
    }
  };

  const now = Date.now();
  const futureTimes = times.filter((t) => t.endTime > now).slice(0, 12);

  return (
    <div className="app-page">
      <Card
        title="秒杀管理"
        extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>新增秒杀活动</Button>}
      >
        <Table
          rowKey="timeId"
          dataSource={futureTimes}
          pagination={false}
          columns={[
            { title: '场次', dataIndex: 'timeId', width: 70 },
            {
              title: '时间段', width: 260,
              render: (_, t) => `${fmtTime(t.startTime)} ~ ${fmtTime(t.endTime)}`
            },
            {
              title: '状态', width: 110,
              render: (_, t) => (
                t.startTime > now ? <Tag color="orange">未开始</Tag>
                  : (t.endTime > now ? <Tag color="green">进行中</Tag> : <Tag>已结束</Tag>)
              )
            },
            { title: '说明', render: () => <span style={{ color: '#8c8ca6' }}>秒杀活动从整点开始，持续 1 小时；库存由 Redis Lua 原子预扣 + MQ 异步落库</span> }
          ]}
        />
      </Card>

      <Modal title="新增秒杀活动" open={open} onCancel={() => setOpen(false)} onOk={submit} okText="创建" cancelText="取消">
        <Form form={form} labelCol={{ span: 6}} initialValues={{ seckillStock: 100, seckillPrice: 999, offsetHours: 1 }}>
          <Form.Item name="productId" label="商品" rules={[{ required: true }]}>
            <Select showSearch options={products} placeholder="选择要参与秒杀的商品" />
          </Form.Item>
          <Form.Item name="seckillPrice" label="秒杀价" rules={[{ required: true }]}>
            <InputNumber min={0.01} precision={2} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="seckillStock" label="秒杀库存" rules={[{ required: true }]}>
            <InputNumber min={1} precision={0} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="offsetHours" label="几小时后开始" extra="从下一个整点起算，活动持续 1 小时">
            <InputNumber min={1} max={48} precision={0} style={{ width: 160 }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}