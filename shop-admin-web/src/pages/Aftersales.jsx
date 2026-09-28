import React, { useEffect, useState } from 'react';
import { Card, Table, Select, Space, Tag, Button, Modal, Input, message } from 'antd';
import api from '../api';

const { TextArea } = Input;

const STATUS = {
  0: { text: '待处理', color: 'orange' },
  1: { text: '已退款', color: 'green' },
  2: { text: '已拒绝', color: 'red' }
};

const fmtTime = (ms) => {
  if (!ms) return '-';
  const d = new Date(ms);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())} ${p(d.getHours())}:${p(d.getMinutes())}`;
};

export default function Aftersales() {
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(false);
  const [rejectTarget, setRejectTarget] = useState(null); // 正在拒绝的售后单
  const [rejectReason, setRejectReason] = useState('');

  const load = () => {
    setLoading(true);
    api.get('/admin/aftersale/page', { params: { page, size: 10, status: status ?? undefined } })
      .then((d) => {
        const data = d || {};
        setRows(data.list || []);
        setTotal(data.total || 0);
      })
      .catch((e) => message.error(e.message))
      .finally(() => setLoading(false));
  };

  useEffect(() => { load(); }, [page, status]);

  const approve = (row) => {
    Modal.confirm({
      title: '同意退款',
      content: `确定同意售后单 ${row.aftersaleId} 的退款申请吗？退款金额 ¥${row.refundAmount}`,
      okText: '同意退款',
      cancelText: '取消',
      onOk: () => api.post('/admin/aftersale/handle', { aftersaleId: row.aftersaleId, action: 'approve' })
        .then(() => { message.success('已同意退款'); load(); })
        .catch((e) => message.error(e.message))
    });
  };

  const submitReject = () => {
    if (!rejectReason.trim()) { message.warning('请填写拒绝理由'); return; }
    api.post('/admin/aftersale/handle', { aftersaleId: rejectTarget.aftersaleId, action: 'reject', reason: rejectReason.trim() })
      .then(() => {
        message.success('已拒绝该售后申请');
        setRejectTarget(null);
        setRejectReason('');
        load();
      })
      .catch((e) => message.error(e.message));
  };

  return (
    <div className="app-page">
      <Card
        title="售后管理"
        extra={
          <Select allowClear placeholder="全部状态" style={{ width: 140 }} value={status}
            onChange={(v) => { setStatus(v); setPage(1); }}
            options={Object.entries(STATUS).map(([k, v]) => ({ value: Number(k), label: v.text }))} />
        }
      >
        <Table
          rowKey="id"
          dataSource={rows}
          loading={loading}
          pagination={{ current: page, total, pageSize: 10, onChange: setPage, showTotal: (t) => `共 ${t} 条` }}
          columns={[
            { title: '售后单号', dataIndex: 'aftersaleId', width: 200 },
            { title: '用户ID', dataIndex: 'userId', width: 80 },
            { title: '订单号', dataIndex: 'orderId', width: 190 },
            {
              title: '商品', width: 220,
              render: (_, r) => (
                <Space>
                  {r.productPicture ? <img src={r.productPicture} alt="" style={{ width: 32, height: 32, objectFit: 'cover', borderRadius: 4 }} /> : null}
                  <span>{r.productName || `#${r.productId}`}</span>
                </Space>
              )
            },
            { title: '退款金额', dataIndex: 'refundAmount', width: 100, render: (v) => `¥${v}` },
            { title: '申请理由', dataIndex: 'reason', ellipsis: true },
            { title: '申请时间', width: 150, render: (_, r) => fmtTime(r.applyTime) },
            {
              title: '状态', width: 130,
              render: (_, r) => {
                const s = STATUS[r.status] || { text: r.status, color: 'default' };
                return (
                  <Space direction="vertical" size={0}>
                    <Tag color={s.color}>{s.text}</Tag>
                    {r.status === 2 && r.rejectReason ? <span style={{ fontSize: 12, color: '#999' }}>拒绝：{r.rejectReason}</span> : null}
                  </Space>
                );
              }
            },
            {
              title: '操作', width: 140, fixed: 'right',
              render: (_, r) => r.status === 0 ? (
                <Space>
                  <Button type="primary" size="small" onClick={() => approve(r)}>同意</Button>
                  <Button danger size="small" onClick={() => { setRejectTarget(r); setRejectReason(''); }}>拒绝</Button>
                </Space>
              ) : <span style={{ color: '#ccc' }}>已处理</span>
            }
          ]}
        />
      </Card>

      <Modal
        title={`拒绝售后单 ${rejectTarget?.aftersaleId || ''}`}
        open={!!rejectTarget}
        onOk={submitReject}
        onCancel={() => { setRejectTarget(null); setRejectReason(''); }}
        okText="提交拒绝"
        cancelText="取消"
        okButtonProps={{ danger: true }}
      >
        <TextArea
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          placeholder="请填写拒绝理由（必填，将反馈给用户）"
          rows={3}
          maxLength={200}
          showCount
        />
      </Modal>
    </div>
  );
}