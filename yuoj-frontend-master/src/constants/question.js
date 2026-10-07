// 支持的语言
export const LANGUAGE_OPTIONS = [
  { label: 'Java', value: 'java' },
  { label: 'C++', value: 'cpp' },
  { label: 'Go', value: 'go' },
];

export const LANGUAGE_MAP = Object.fromEntries(
  LANGUAGE_OPTIONS.map((item) => [item.value, item.label])
);

// 提交状态（后端约定：0 待判题 / 1 判题中 / 2 成功 / 3 失败）
export const SUBMIT_STATUS_MAP = {
  0: { text: '待判题', color: 'gray' },
  1: { text: '判题中', color: 'arcoblue' },
  2: { text: '成功', color: 'green' },
  3: { text: '失败', color: 'red' },
};

export const STATUS_OPTIONS = [
  { label: '待判题', value: 0 },
  { label: '判题中', value: 1 },
  { label: '成功', value: 2 },
  { label: '失败', value: 3 },
];

// 默认判题配置（时间 ms / 内存 KB / 栈 KB）
export const DEFAULT_JUDGE_CONFIG = {
  timeLimit: 10000,
  memoryLimit: 65536,
  stackLimit: 10000,
};

export const DEFAULT_CODE = {
  java: `import java.util.Scanner;

public class Main {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        // 在此编写你的代码
    }
}
`,
  cpp: `#include <iostream>
using namespace std;

int main() {
    // 在此编写你的代码
    return 0;
}
`,
  go: `package main

import "fmt"

func main() {
    // 在此编写你的代码
    fmt.Println("Hello, World!")
}
`,
};

export const USER_ROLE_OPTIONS = [
  { label: '普通用户', value: 'user' },
  { label: '管理员', value: 'admin' },
];

export const USER_ROLE_MAP = Object.fromEntries(
  USER_ROLE_OPTIONS.map((item) => [item.value, item.label])
);
