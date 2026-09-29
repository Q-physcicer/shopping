import React, { useEffect, useState } from 'react';
import { Card, Table, Button, Input, Select, Modal, Form, InputNumber, message, Space, Tag } from 'antd';
import { PlusOutlined, SearchOutlined } from '@ant-design/icons';
import api from '../api';

export default function Products() {
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');
  const [searchKeyword, setSearchKeyword] = useState('');   // 提交搜索的词（Enter 才生效）
  const [loading, setLoading] = useState(false);
  const [categories, setCategories] = useState([]);
  const [categoryId, setCategoryId] = useState(null);
  const [editOpen, setEditOpen] = useState(false);
  const [editing, setEditing] = useState(null);   // null=新增, 对象=编辑
  const [submitting, setSubmitting] = useState(false);
  const [form] = Form.useForm();

  // P1 修复：全部过滤条件都进依赖（原 setTimeout(load) 闭包读取旧 categoryId，筛选永远慢一拍）
  useEffect(() => {
    setLoading(true);
    api.get('/admin/product/page', {
      params: { page, size: 10, keyword: searchKeyword || undefined, categoryId: categoryId || undefined }
    })
      .then((d) => { setRows(d.list || []); setTotal(d.total || 0); })
      .catch((e) => message.error(e.message))
      .finally(() => setLoading(false));
  }, [page, searchKeyword, categoryId]);

  useEffect(() => {
    api.get('/admin/category/list').then(setCategories).catch(() => {});
  }, []);

  const openEdit = (record) => {
    setEditing(record || null);
    // P1 修复：先重置再填充（原 setFieldsValue 不清未提供字段，新增会继承上一次编辑的名称/简介）
    form.resetFields();
    form.setFieldsValue(record || { productNum: 100, productPrice: 100, productSellingPrice: 100 });
    setEditOpen(true);
  };

  const submit = async () => {
    const values = await form.validateFields();
    if (submitting) return;
    setSubmitting(true);
    try {
      if (editing?.productId) {
        const res = await api.post(`/admin/product/${editing.productId}/update`, values);
        message.success((res && res.__msg) || '商品已更新（ES 同步已触发）');
      } else {
        const res = await api.post('/admin/product', values);
        message.success((res && res.__msg) || '商品上架成功');
      }
      setEditOpen(false);
      setPage(1);
    } catch (e) {
      message.error(e.message);
    } finally {
      setSubmitting(false);
    }
  };

  const offline = (record) => {
    Modal.confirm({
      title: `下架「${record.productName}」？`,
      content: '库存将置 0，并自动移出搜索索引',
      onOk: async () => {
        try {
          const res = await api.delete(`/admin/product/${record.productId}`);
          message.success((res && res.__msg) || '已下架');
          setPage(1);
        } catch (e) { message.error(e.message); }
      }
    });
  };

  return (
    <div className="app-page">
      <Card
        title="商品管理"
        extra={
          <Space>
            <Input allowClear prefix={<SearchOutlined />} placeholder="商品名搜索（回车）"
              value={keyword} onChange={(e) => setKeyword(e.target.value)}
              onPressEnter={() => { setPage(1); setSearchKeyword(keyword.trim()); }} style={{ width: 200 }} />
            <Select allowClear placeholder="分类" style={{ width: 130 }} value={categoryId}
              onChange={(v) => { setPage(1); setCategoryId(v); }}
              options={categories.map((c) => ({ value: c.categoryId, label: c.categoryName }))} />
            <Button type="primary" icon={<PlusOutlined />} onClick={() => openEdit(null)}>上架商品</Button>
          </Space>
        }
      >
        <Table
          rowKey="productId"
          loading={loading}
          dataSource={rows}
          pagination={{ current: page, total, pageSize: 10, onChange: setPage, showTotal: (t) => `共 ${t} 件商品` }}
          columns={[
            { title: 'ID', dataIndex: 'productId', width: 60 },
            {
              title: '图', dataIndex: 'productPicture', width: 70,
              render: (v) => v ? <img src={v} alt=""
                style={{ width: 44, height: 44, objectFit: 'contain' }} /> : '-'
            },
            { title: '商品', dataIndex: 'productName' },
            { title: '分类', dataIndex: 'categoryId', width: 90,
              render: (id) => (categories.find((c) => c.categoryId === id)?.categoryName) || id },
            { title: '原价', dataIndex: 'productPrice', width: 90, render: (v) => `¥${v}` },
            { title: '售价', dataIndex: 'productSellingPrice', width: 90, render: (v) => `¥${v}` },
            { title: '库存', dataIndex: 'productNum', width: 80,
              render: (v) => (v === 0 ? <Tag color="red">已下架</Tag> : (v < 5 ? <Tag color="orange">{v} 件</Tag> : v)) },
            { title: '销量', dataIndex: 'productSales', width: 70 },
            {
              title: '操作', width: 150,
              render: (_, r) => (
                <Space>
                  <a onClick={() => openEdit(r)}>编辑</a>
                  {r.productNum > 0 && <a style={{ color: '#fa541c' }} onClick={() => offline(r)}>下架</a>}
                </Space>
              )
            }
          ]}
        />
      </Card>

      <Modal title={editing?.productId ? `编辑商品 #${editing.productId}` : '上架新商品'}
        open={editOpen} onCancel={() => setEditOpen(false)} onOk={submit}
        okText="保存" cancelText="取消" width={560} confirmLoading={submitting}>
        <Form form={form} labelCol={{ span: 6 }}>
          <Form.Item name="productName" label="商品名称"
            rules={[{ required: true, message: '请输入商品名称' }, { max: 100, message: '不超过 100 字' }]}>
            <Input placeholder="如：星选手机 X10" maxLength={100} />
          </Form.Item>
          <Form.Item name="categoryId" label="分类" rules={[{ required: true, message: '请选择分类' }]}>
            <Select options={categories.map((c) => ({ value: c.categoryId, label: c.categoryName }))} />
          </Form.Item>
          {/* P1 修复：移除外层 Space + labelCol 组合导致的错位挤压，改纵向排列 */}
          <Form.Item name="productPrice" label="原价"
            rules={[{ required: true, message: '请输入原价' }]}>
            <InputNumber min={0.01} precision={2} style={{ width: 160 }} placeholder="大于 0" />
          </Form.Item>
          <Form.Item name="productSellingPrice" label="售价"
            rules={[{ required: true, message: '请输入售价' }]}>
            <InputNumber min={0.01} precision={2} style={{ width: 160 }} placeholder="大于 0" />
          </Form.Item>
          <Form.Item name="productNum" label="库存"
            rules={[{ required: true, message: '请输入库存' }]}>
            <InputNumber min={0} precision={0} style={{ width: 160 }} />
          </Form.Item>
          <Form.Item name="productPicture" label="图片路径">
            <Input placeholder="imgs/goods/xxx.svg" />
          </Form.Item>
          <Form.Item name="productIntro" label="卖点简介">
            <Input.TextArea rows={3} maxLength={500} placeholder="分卖点 / 分关键词 / 分隔" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
}
