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
  const [products, setProducts] = useState([]);
  const [open, setOpen] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  // P2：状态随时间自动刷新（原 render 时求值，页面挂着不动时"进行中"一直显示到手动刷新）
  const [now, setNow] = useState(Date.now());
  const [form] = Form.useForm();

  useEffect(() => {
    const timer = setInterval(() => setNow(Date.now()), 60000);
    return () => clearInterval(timer);
  }, []);

  const load = () => {
    api.get('/admin/seckill/time').then((t) => {
      setTimes(t || []);
    }).catch((e) => message.error(e.message || '场次加载失败'));
    // 商品下拉（P2：showSearch + 本地过滤，可搜索第 100 个以后的商品）
    api.get('/admin/product/page', { params: { page: 1, size: 100 } }).then((d) => {
      setProducts((d.list || []).map((p) => ({ value: p.productId, label: `#${p.productId} ${p.productName}` })));
    }).catch(() => {});
  };

  useEffect(() => { load(); }, []);

  const submit = async () => {
    const v = await form.validateFields();
    if (submitting) return;
    setSubmitting(true);
    try {
      // P1：固定文案提示（原用返回对象当文案，渲染 [object Object]）
      await api.post('/admin/seckill', v);
      message.success('秒杀活动已创建（手动场，不会被每日定时重建清除）');
      setOpen(false);
      load();
    } catch (e) {
      message.error(e.message);
    } finally {
      setSubmitting(false);
    }
  };

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
            {
              title: '来源', dataIndex: 'source', width: 90,
              render: (v) => (v === 'manual'
                ? <Tag color="purple">手动场</Tag>
                : <Tag color="blue">定时场</Tag>)
            },
            { title: '说明', render: () => <span style={{ color: '#8c8ca6' }}>整点开始持续 1 小时；Redis Lua 原子预扣 + MQ 异步落库；定时场每日 15:00 重建，手动场保留</span> }
          ]}
        />
      </Card>

      <Modal title="新增秒杀活动" open={open} onCancel={() => setOpen(false)} onOk={submit}
        okText="创建" cancelText="取消" confirmLoading={submitting}>
        <Form form={form} labelCol={{ span: 6 }} initialValues={{ seckillStock: 100, seckillPrice: 999, offsetHours: 1 }}>
          <Form.Item name="productId" label="商品" rules={[{ required: true, message: '请选择商品' }]}>
            <Select showSearch optionFilterProp="label" options={products} placeholder="搜索并选择要参与秒杀的商品" />
          </Form.Item>
          <Form.Item name="seckillPrice" label="秒杀价" rules={[{ required: true, message: '请输入秒杀价' }]}
            extra="须不高于商品当前售价">
            <InputNumber min={0.01} precision={2} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="seckillStock" label="秒杀库存" rules={[{ required: true, message: '请输入库存' }]}
            extra="须不高于商品当前库存">
            <InputNumber min={1} precision={0} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="offsetHours" label="几小时后开始" extra="从下一个整点起算，活动持续 1 小时">
            <InputNumber min={1} max={72} precision={0} style={{ width: 160 }} />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
