module.exports = {
  root: true,
  env: {
    node: true
  },
  'extends': [
    'plugin:vue/essential',
    'eslint:recommended'
  ],
  rules: {
    'no-console': process.env.NODE_ENV === 'production' ? 'error' : 'off',
    'no-debugger': process.env.NODE_ENV === 'production' ? 'error' : 'off'
  },
  parserOptions: {
    parser: '@babel/eslint-parser', // 替换为新解析器
    ecmaVersion: 'latest',          // 允许解析最新 ES 语法
    sourceType: 'module',           // 支持 ES 模块
    ecmaFeatures: {
      jsx: true                     // 如果项目使用 JSX（如 Vue 单文件组件）
    }
  }
}