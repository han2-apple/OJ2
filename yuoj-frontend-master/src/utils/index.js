export function formatDate(dateStr) {
  if (!dateStr) return '-';
  const date = new Date(dateStr);
  if (Number.isNaN(date.getTime())) return '-';
  const pad = (n) => String(n).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ` +
    `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

export function parseJson(str, fallback = null) {
  if (str === null || str === undefined || str === '') return fallback;
  try {
    return JSON.parse(str);
  } catch (e) {
    return fallback;
  }
}

export function parseJsonArray(str) {
  const value = parseJson(str, []);
  return Array.isArray(value) ? value : [];
}

export function parseJsonObject(str, fallback = {}) {
  const value = parseJson(str, null);
  return value && typeof value === 'object' && !Array.isArray(value)
    ? value
    : fallback;
}

export function calcPassRate(acceptedNum, submitNum) {
  if (!submitNum) return '0.0%';
  return `${((acceptedNum / submitNum) * 100).toFixed(1)}%`;
}

export function formatTimeLimit(ms) {
  const num = Number(ms);
  if (!num) return '-';
  if (num % 1000 === 0) return `${num / 1000}s`;
  return `${num}ms`;
}

export function formatMemoryLimit(kb) {
  const num = Number(kb);
  if (!num) return '-';
  if (num % 1024 === 0) return `${num / 1024}MB`;
  return `${kb}KB`;
}

const JUDGE_MESSAGE_MAP = {
  Accepted: '通过',
  'Compile Error': '编译错误',
  'Runtime Error': '运行错误',
  'Wrong Answer': '答案错误',
  'Time Limit Exceeded': '运行超时',
  'Memory Limit Exceeded': '内存超限',
  'Output Limit Exceeded': '输出超限',
  'Presentation Error': '格式错误',
  'System Error': '系统错误',
  'Unknown Error': '未知错误',
};

export function formatJudgeMessage(message) {
  if (!message) return '-';
  return JUDGE_MESSAGE_MAP[message] || message;
}
