import React, { useEffect, useState } from 'react';
import { Card, Table, Button, Modal, Form, Input, message, Popconfirm } from 'antd';
import { PlusOutlined } from '@ant-design/icons';
import api from '../api';

/** 轮播管理：图片沿用 $target 前缀的静态资源（public/imgs/...） */
const IMG_HOST = '/';

export default function Carousels() {
  const [rows, setRows] = useState([]);
  const [open, setOpen] = useState(false);
  const [form] = Form.useForm();

  const load = () => {
    api.get('/resources/carousel').then(setRows).catch((e) => message.error(e.message));
  };

  useEffect(() => { load(); }, []);

  const submit = async () => {
    const v = await form.validateFields();
    try {
      await api.post('/admin/carousel', { imgPath: v.imgPath, describes: v.describes || null });
      message.success('轮播已新增（购物端即时生效）');
      setOpen(false);
      form.resetFields();
      load();
    } catch (e) { message.error(e.message); }
  };

  const del = async (id) => {
    try {
      await api.delete(`/admin/carousel/${id}`);
      message.success('已删除');
      load();
    } catch (e) { message.error(e.message); }
  };

  return (
    <div className="app-page">
      <Card title="首页轮播管理" extra={<Button type="primary" icon={<PlusOutlined />} onClick={() => setOpen(true)}>新增轮播</Button>}>
        <Table
          rowKey="carouselId"
          dataSource={rows}
          pagination={false}
          columns={[
            { title: 'ID', dataIndex: 'carouselId', width: 70 },
            {
              title: '预览', width: 200,
              render: (_, r) => (
                <img src={IMG_HOST + r.imgPath} alt={r.describes || ''}
                  style={{ width: 180, height: 70, objectFit: 'cover', borderRadius: 8 }} />
              )
            },
            { title: '图片路径', dataIndex: 'imgPath' },
            { title: '描述', dataIndex: 'describes', render: (v) => v || '-' },
            {
              title: '操作', width: 100,
              render: (_, r) => <Popconfirm title="删除该轮播？" onConfirm={() => del(r.carouselId)}>
                <a style={{ color: '#fa541c' }}>删除</a>
              </Popconfirm>
            }
          ]}
        />
      </Card>

      <Modal title="新增轮播" open={open} onCancel={() => setOpen(false)} onOk={submit} okText="保存" cancelText="取消">
        <Form form={form} labelCol={{ span: 5 }}>
          <Form.Item name="imgPath" label="图片路径" rules={[{ required: true }]}
            extra="相对路径，如 public/imgs/cms_5.jpg（图片请先放置到静态资源目录）">
            <Input placeholder="public/imgs/xxx.jpg" />
          </Form.Item>
          <Form.Item name="describes" label="描述">
            <Input placeholder="可选" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}