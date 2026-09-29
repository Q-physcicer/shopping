import React, { useEffect, useRef, useState } from 'react';
import { Card, Input, Button, message } from 'antd';
import { SendOutlined, ClearOutlined, PauseOutlined } from '@ant-design/icons';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import api from '../api';

/**
 * 管理端 AI 运营助手（P5）：
 * SSE 经网关 /api/agent/admin/stream（网关校验 ADMIN，EventSource 靠 XM_TOKEN cookie 认证）。
 * 管理 Agent 可：查在售、上架商品、改价改库存、拉取经营统计。
 */
export default function AgentChat() {
  const [msgs, setMsgs] = useState([]);
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const cidRef = useRef('adm-' + Date.now());
  const boxRef = useRef(null);
  const esRef = useRef(null);

  useEffect(() => {
    return () => esRef.current && esRef.current.close();
  }, []);

  useEffect(() => {
    if (boxRef.current) {
      boxRef.current.scrollTop = boxRef.current.scrollHeight;
    }
  }, [msgs]);

  const send = (text) => {
    if (!text.trim() || streaming) return;
    setMsgs((m) => [...m, { role: 'me', content: text }]);
    setInput('');
    setStreaming(true);

    // 逐 token 流式追加
    setMsgs((m) => [...m, { role: 'bot', content: '' }]);
    const es = new EventSource(
      `/api/agent/admin/stream?message=${encodeURIComponent(text)}&conversationId=${cidRef.current}`
    );
    esRef.current = es;

    es.addEventListener('content', (e) => {
      setMsgs((m) => {
        const copy = [...m];
        copy[copy.length - 1] = {
          role: 'bot',
          content: copy[copy.length - 1].content + e.data
        };
        return copy;
      });
    });
    es.addEventListener('done', () => {
      es.close();
      esRef.current = null;
      setStreaming(false);
    });
    es.onerror = () => {
      es.close();
      esRef.current = null;
      setStreaming(false);
      setMsgs((m) => {
        const copy = [...m];
        if (copy[copy.length - 1]?.role === 'bot' && !copy[copy.length - 1].content) {
          copy[copy.length - 1] = { role: 'bot', content: '连接中断，请重试（需以管理员身份登录）' };
        }
        return copy;
      });
    };
  };

  const reset = async () => {
    // P1：改走 api 实例（原原生 fetch 不验状态码，cookie 失效时 reset 假成功——实际记忆没清）
    try {
      await api.post('/agent/admin/reset', { conversationId: cidRef.current });
      setMsgs([]);
      message.success('会话已重置');
    } catch (e) {
      message.error(e.message || '重置失败');
    }
  };

  // 停止生成：close EventSource 后自行收尾（close 不触发 done/error）；保留已生成内容
  const stop = () => {
    if (esRef.current) {
      esRef.current.close();
      esRef.current = null;
    }
    setStreaming(false);
  };

  return (
    <div className="app-page" style={{ height: 'calc(100vh - 56px)' }}>
      <Card
        title="AI 运营助手"
        extra={<Button icon={<ClearOutlined />} onClick={reset}>清空会话</Button>}
        bodyStyle={{ display: 'flex', flexDirection: 'column', height: 'calc(100% - 58px)' }}
        style={{ height: '100%' }}
      >
        <div className="agent-chat">
          <div className="hint" style={{ marginBottom: 10, color: '#8c8ca6', fontSize: 13, lineHeight: 1.8 }}>
            试试说：「现在总GMV多少？」、「上架一个新品蓝牙耳机，售价 199，库存 50」、「把商品 3 的售价改成 2299」
          </div>
          <div className="agent-msgs" ref={boxRef}>
            {msgs.map((m, i) => (
              <div key={i} className={`agent-msg ${m.role === 'me' ? 'me' : 'bot'}`}>
                <div className="avatar">{m.role === 'me' ? '我' : 'AI'}</div>
                <div className="content md">
                  {m.role === 'me' ? m.content : <ReactMarkdown remarkPlugins={[remarkGfm]}>{m.content || ''}</ReactMarkdown>}
                  {m.role === 'bot' && i === msgs.length - 1 && streaming ? '▍' : ''}
                </div>
              </div>
            ))}
          </div>
          <div style={{ display: 'flex', gap: 10, marginTop: 12 }}>
            <Input.Search
              enterButton={streaming ? <PauseOutlined /> : <SendOutlined />}
              placeholder="输入指令，Enter 发送"
              value={input}
              loading={streaming}
              onChange={(e) => setInput(e.target.value)}
              onSearch={streaming ? stop : send}
            />
            {streaming && (
              <Button danger icon={<PauseOutlined />} onClick={stop}>停止</Button>
            )}
          </div>
        </div>
      </Card>
    </div>
  );
}