import React from 'react';
import { BrowserRouter, Routes, Route, Navigate, Link, useLocation } from 'react-router-dom';
import { ProLayout } from '@ant-design/pro-components';
import {
  DashboardOutlined, ShoppingOutlined, ThunderboltOutlined,
  PictureOutlined, FileTextOutlined, UserOutlined,
  RobotOutlined, SettingOutlined, LogoutOutlined, CustomerServiceOutlined
} from '@ant-design/icons';
import { Button } from 'antd';
import api, { currentUser, logout } from './api';
import Login from './pages/Login';
import Dashboard from './pages/Dashboard';
import Products from './pages/Products';
import Seckill from './pages/Seckill';
import Carousels from './pages/Carousels';
import Orders from './pages/Orders';
import Aftersales from './pages/Aftersales';
import Users from './pages/Users';
import AgentChat from './pages/AgentChat';
import Settings from './pages/Settings';

const MENU = {
  '/': { path: '/', name: '数据看板', icon: <DashboardOutlined /> },
  '/products': { path: '/products', name: '商品管理', icon: <ShoppingOutlined /> },
  '/seckill': { path: '/seckill', name: '秒杀管理', icon: <ThunderboltOutlined /> },
  '/carousels': { path: '/carousels', name: '轮播管理', icon: <PictureOutlined /> },
  '/orders': { path: '/orders', name: '订单管理', icon: <FileTextOutlined /> },
  '/aftersales': { path: '/aftersales', name: '售后管理', icon: <CustomerServiceOutlined /> },
  '/users': { path: '/users', name: '用户管理', icon: <UserOutlined /> },
  '/agent': { path: '/agent', name: 'AI 运营助手', icon: <RobotOutlined /> },
  '/settings': { path: '/settings', name: '系统设置', icon: <SettingOutlined /> }
};

function Shell({ children }) {
  const user = currentUser();
  const location = useLocation();
  return (
    <ProLayout
      title="星选商城 · 管理端"
      logo={<span style={{ fontSize: 22 }}>🛍️</span>}
      layout="mix"
      location={{ pathname: location.pathname }}
      menu={{ request: async () => Object.values(MENU) }}
      /* 菜单点击接管：ProLayout 默认不与外部 router 联动，必须用 Link 渲染菜单项才可切换路由 */
      menuItemRender={(item, dom) => <Link to={item.path}>{dom}</Link>}
      avatarProps={{
        title: user?.username || '管理员',
        render: (_, dom) => (
          <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
            {dom}
            <Button size="small" icon={<LogoutOutlined />} onClick={logout}>退出</Button>
          </div>
        )
      }}
    >
      {children}
    </ProLayout>
  );
}

function RequireAdmin({ children }) {
  const user = currentUser();
  if (!user || user.role !== 'ADMIN') {
    return <Navigate to="/login" replace />;
  }
  return children;
}

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route path="/" element={<RequireAdmin><Shell><Dashboard /></Shell></RequireAdmin>} />
        <Route path="/products" element={<RequireAdmin><Shell><Products /></Shell></RequireAdmin>} />
        <Route path="/seckill" element={<RequireAdmin><Shell><Seckill /></Shell></RequireAdmin>} />
        <Route path="/carousels" element={<RequireAdmin><Shell><Carousels /></Shell></RequireAdmin>} />
        <Route path="/orders" element={<RequireAdmin><Shell><Orders /></Shell></RequireAdmin>} />
        <Route path="/aftersales" element={<RequireAdmin><Shell><Aftersales /></Shell></RequireAdmin>} />
        <Route path="/users" element={<RequireAdmin><Shell><Users /></Shell></RequireAdmin>} />
        <Route path="/agent" element={<RequireAdmin><Shell><AgentChat /></Shell></RequireAdmin>} />
        <Route path="/settings" element={<RequireAdmin><Shell><Settings /></Shell></RequireAdmin>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}