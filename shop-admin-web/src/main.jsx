import React from 'react';
import ReactDOM from 'react-dom/client';
import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import App from './App';
import ErrorBoundary from './ErrorBoundary';
import './global.css';

ReactDOM.createRoot(document.getElementById('root')).render(
  <ErrorBoundary>
    <ConfigProvider
      locale={zhCN}
      theme={{
        token: {
          colorPrimary: '#5b6ef5',
          borderRadius: 8
        }
      }}
    >
      <App />
    </ConfigProvider>
  </ErrorBoundary>
);