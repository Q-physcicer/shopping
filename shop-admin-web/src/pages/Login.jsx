import React, { useState } from 'react';
import { Card, Form, Input, Button, message } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { login } from '../api';

export default function Login() {
  const [loading, setLoading] = useState(false);

  const onFinish = async ({ username, password }) => {
    setLoading(true);
    try {
      const user = await login(username, password);
      if (user?.role !== 'ADMIN') {
        message.error('该账号不是管理员');
        localStorage.removeItem('ADMIN_JWT');
        localStorage.removeItem('ADMIN_USER');
        return;
      }
      message.success(`欢迎回来，${user.username}`);
      // 回跳 401 前的页面（带 query 透传）；无 redirect 回首页
      const redirect = new URLSearchParams(window.location.search).get('redirect');
      window.location.href = redirect && redirect.startsWith('/') ? redirect : '/';
    } catch (e) {
      message.error(e.message || '登录失败');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{
      height: '100vh', display: 'flex', alignItems: 'center', justifyContent: 'center',
      background: 'linear-gradient(135deg, #5b6ef5 0%, #8f6ef5 100%)'
    }}>
      <Card style={{ width: 380, boxShadow: '0 20px 60px rgba(0,0,0,.25)' }} styles={{ body: { padding: 32 } }}>
        <div style={{ textAlign: 'center', marginBottom: 24 }}>
          <span style={{ fontSize: 40 }}>🛍️</span>
          <h2 style={{ margin: '8px 0 4px', color: '#2b2b38' }}>星选商城</h2>
          <div style={{ color: '#8c8ca6' }}>管理端登录</div>
        </div>
        <Form onFinish={onFinish} size="large">
          <Form.Item name="username" rules={[{ required: true, message: '请输入用户名' }]}>
            <Input prefix={<UserOutlined />} placeholder="管理员用户名" />
          </Form.Item>
          <Form.Item name="password" rules={[{ required: true, message: '请输入密码' }]}>
            <Input.Password prefix={<LockOutlined />} placeholder="密码" />
          </Form.Item>
          <Button type="primary" htmlType="submit" block loading={loading}>登 录</Button>
        </Form>
      </Card>
    </div>
  );
}