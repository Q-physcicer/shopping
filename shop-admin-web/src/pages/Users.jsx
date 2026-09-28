import React, { useEffect, useState } from 'react';
import { Card, Table, Button, Input, Tag, message, Popconfirm } from 'antd';
import { SearchOutlined } from '@ant-design/icons';
import api from '../api';

export default function Users() {
  const [rows, setRows] = useState([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [keyword, setKeyword] = useState('');

  const load = () => {
    api.get('/admin/user/page', { params: { page, size: 10, keyword: keyword || undefined } })
      .then((d) => { setRows(d.list || []); setTotal(d.total || 0); })
      .catch((e) => message.error(e.message));
  };

  useEffect(() => { load(); }, [page]);

  const toggleRole = async (u) => {
    try {
      const res = await api.post(`/admin/user/${u.userId}/role`);
      message.success(res || '已切换');
      load();
    } catch (e) { message.error(e.message); }
  };

  return (
    <div className="app-page">
      <Card
        title="用户管理"
        extra={
          <Input allowClear prefix={<SearchOutlined />} placeholder="用户名搜索" style={{ width: 200 }}
            value={keyword} onChange={(e) => setKeyword(e.target.value)}
            onPressEnter={() => { setPage(1); load(); }} />
        }
      >
        <Table
          rowKey="userId"
          dataSource={rows}
          pagination={{ current: page, total, pageSize: 10, onChange: setPage }}
          columns={[
            { title: 'ID', dataIndex: 'userId', width: 60 },
            { title: '用户名', dataIndex: 'username' },
            {
              title: '角色', width: 100,
              render: (_, u) => (u.role === 'ADMIN'
                ? <Tag color="geekblue">ADMIN</Tag> : <Tag>USER</Tag>)
            },
            {
              title: '操作', width: 140,
              render: (_, u) => (
                <Popconfirm
                  title={u.role === 'ADMIN' ? '降级为普通用户？' : '提权为管理员？'}
                  onConfirm={() => toggleRole(u)}>
                  <a style={{ color: u.role === 'ADMIN' ? '#fa541c' : '#5b6ef5' }}>
                    {u.role === 'ADMIN' ? '降级为 USER' : '设为 ADMIN'}
                  </a>
                </Popconfirm>
              )
            }
          ]}
        />
      </Card>
    </div>
  );
}