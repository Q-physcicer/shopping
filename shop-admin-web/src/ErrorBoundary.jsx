import React from 'react';
import { Result, Button } from 'antd';

/**
 * 全站错误边界（P2）：渲染期异常降级为可恢复 UI 而非整站白屏。
 */
export default class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { error: null };
  }

  static getDerivedStateFromError(error) {
    return { error };
  }

  componentDidCatch(error, info) {
    // eslint-disable-next-line no-console
    console.error('[ErrorBoundary]', error, info);
  }

  render() {
    if (this.state.error) {
      return (
        <Result
          status="error"
          title="页面渲染异常"
          subTitle={String(this.state.error?.message || this.state.error)}
          extra={[
            <Button key="retry" type="primary" onClick={() => this.setState({ error: null })}>
              重试渲染
            </Button>,
            <Button key="home" onClick={() => { window.location.href = '/'; }}>
              返回首页
            </Button>
          ]}
        />
      );
    }
    return this.props.children;
  }
}
