import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 向 yuoj 数据库批量写入 OJ 题目：题面、标签、参考答案、测试用例、判题配置。
 *
 * 运行前会先用本机 javac / g++ 编译每道题的参考答案，并把所有测试用例分别以
 * 「标准输入」和「命令行参数」两种方式跑一遍（对应不同的代码沙箱实现），
 * 输出不一致时会直接报错、不写数据库，保证库里存的用例和答案自洽。
 *
 * 用法：
 *   java -cp "<mysql-connector.jar>" tools/seed/SeedQuestions.java
 *   java -cp "<mysql-connector.jar>" tools/seed/SeedQuestions.java --replace      # 覆盖同名题目
 *   java -cp "<mysql-connector.jar>" tools/seed/SeedQuestions.java --skip-verify  # 跳过本地校验
 *   java -cp "<mysql-connector.jar>;target/classes;<hutool.jar>" tools/seed/SeedQuestions.java --check-only
 */
public class SeedQuestions {

    private static final String JDBC_URL = "jdbc:mysql://localhost:3306/yuoj"
            + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai"
            + "&useSSL=false&allowPublicKeyRetrieval=true";
    private static final String JDBC_USER = "root";
    private static final String JDBC_PASSWORD = "128219";

    /** 题目创建人，取库里已有的用户 hanhan */
    private static final long CREATOR_ID = 2095408042155114497L;

    /** 与 QuestionServiceImpl#validQuestion 中的限制保持一致 */
    private static final int MAX_TITLE_LENGTH = 80;
    private static final int MAX_TEXT_LENGTH = 8192;

    private static PrintStream out;

    public static void main(String[] args) throws Exception {
        out = new PrintStream(new FileOutputStream(FileDescriptor.out), true, StandardCharsets.UTF_8);
        List<String> options = Arrays.asList(args);
        boolean replace = options.contains("--replace");
        boolean skipVerify = options.contains("--skip-verify");
        boolean checkOnly = options.contains("--check-only");

        List<Problem> problems = problems();
        out.println("待写入题目数量：" + problems.size());
        checkLengthLimits(problems);

        if (checkOnly) {
            loadExistingIds(problems);
            postCheck(problems);
            verifyWithProjectDto();
            return;
        }

        if (skipVerify) {
            out.println("已跳过本地编译校验（--skip-verify）");
        } else {
            verifyAll(problems);
        }

        seed(problems, replace);
        postCheck(problems);
        verifyWithProjectDto();
    }

    // ------------------------------------------------------------------
    // 数据模型
    // ------------------------------------------------------------------

    static final class Case {
        final String input;
        final String output;

        Case(String input, String output) {
            this.input = input;
            this.output = output;
        }
    }

    static final class Problem {
        String title;
        List<String> tags;
        String desc;
        String inputDesc;
        String outputDesc;
        String hint;
        String idea;
        String complexity;
        List<Case> cases;
        String javaCode;
        String cppCode;
        int submitNum;
        int acceptedNum;
        long timeLimit;
        long memoryLimit = 256;
        long stackLimit = 128;

        Long id;
        String content;
        String answer;
        String tagsJson;
        String judgeCaseJson;
        String judgeConfigJson;
    }

    private static Problem p(String title, String[] tags, String desc, String inputDesc, String outputDesc,
                             String hint, String idea, String complexity, Case[] cases, String javaCode,
                             String cppCode, int submitNum, int acceptedNum, long timeLimit) {
        Problem problem = new Problem();
        problem.title = title;
        problem.tags = Arrays.asList(tags);
        problem.desc = desc.strip();
        problem.inputDesc = inputDesc.strip();
        problem.outputDesc = outputDesc.strip();
        problem.hint = hint == null ? null : hint.strip();
        problem.idea = idea.strip();
        problem.complexity = complexity.strip();
        problem.cases = Arrays.asList(cases);
        problem.javaCode = javaCode.strip();
        problem.cppCode = cppCode.strip();
        problem.submitNum = submitNum;
        problem.acceptedNum = acceptedNum;
        problem.timeLimit = timeLimit;
        return problem;
    }

    // ------------------------------------------------------------------
    // 题面 / 答案 / JSON 组装
    // ------------------------------------------------------------------

    private static String buildContent(Problem p) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 题目描述\n\n").append(p.desc).append("\n\n");
        sb.append("## 输入描述\n\n").append(p.inputDesc).append("\n\n");
        sb.append("## 输出描述\n\n").append(p.outputDesc).append("\n\n");
        sb.append("## 示例\n\n");
        int show = Math.min(2, p.cases.size());
        for (int i = 0; i < show; i++) {
            Case c = p.cases.get(i);
            sb.append("### 示例 ").append(i + 1).append("\n\n");
            sb.append("输入\n\n```\n").append(c.input).append("\n```\n\n");
            sb.append("输出\n\n```\n").append(c.output).append("\n```\n\n");
        }
        if (p.hint != null && !p.hint.isEmpty()) {
            sb.append("## 提示\n\n").append(p.hint).append("\n\n");
        }
        sb.append("> 提示：代码沙箱以「标准输入」或「命令行参数」方式传入测试数据，两种方式均可读取，")
          .append("且需要把类名/文件名写成 Main（Java 为 public class Main，C++ 直接编译 main.cpp）。\n");
        return sb.toString();
    }

    private static String buildAnswer(Problem p) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 解题思路\n\n").append(p.idea).append("\n\n");
        sb.append("## 参考代码（Java）\n\n```java\n").append(p.javaCode).append("\n```\n\n");
        sb.append("## 参考代码（C++）\n\n```cpp\n").append(p.cppCode).append("\n```\n\n");
        sb.append("## 复杂度\n\n").append(p.complexity).append("\n");
        return sb.toString();
    }

    private static String buildTagsJson(Problem p) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < p.tags.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(jsonString(p.tags.get(i)));
        }
        return sb.append(']').toString();
    }

    private static String buildJudgeCaseJson(Problem p) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < p.cases.size(); i++) {
            Case c = p.cases.get(i);
            if (i > 0) {
                sb.append(',');
            }
            sb.append("{\"input\":").append(jsonString(c.input))
              .append(",\"output\":").append(jsonString(c.output)).append('}');
        }
        return sb.append(']').toString();
    }

    private static String buildJudgeConfigJson(Problem p) {
        return "{\"timeLimit\":" + p.timeLimit
                + ",\"memoryLimit\":" + p.memoryLimit
                + ",\"stackLimit\":" + p.stackLimit + "}";
    }

    private static String jsonString(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"':
                    sb.append("\\\"");
                    break;
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    private static void checkLengthLimits(List<Problem> problems) {
        for (Problem p : problems) {
            p.content = buildContent(p);
            p.answer = buildAnswer(p);
            p.tagsJson = buildTagsJson(p);
            p.judgeCaseJson = buildJudgeCaseJson(p);
            p.judgeConfigJson = buildJudgeConfigJson(p);

            require(p.title.length() <= MAX_TITLE_LENGTH, p.title + "：标题超过 " + MAX_TITLE_LENGTH + " 字");
            require(p.content.length() <= MAX_TEXT_LENGTH, p.title + "：题面超过 " + MAX_TEXT_LENGTH + " 字");
            require(p.answer.length() <= MAX_TEXT_LENGTH, p.title + "：答案超过 " + MAX_TEXT_LENGTH + " 字");
            require(p.judgeCaseJson.length() <= MAX_TEXT_LENGTH,
                    p.title + "：判题用例超过 " + MAX_TEXT_LENGTH + " 字");
            require(p.judgeConfigJson.length() <= MAX_TEXT_LENGTH, p.title + "：判题配置过长");
            require(p.tagsJson.length() <= 1024, p.title + "：标签过长");
            require(p.acceptedNum <= p.submitNum, p.title + "：通过数大于提交数");
            for (Case c : p.cases) {
                require(!c.input.contains("\n") && !c.input.contains("\r"),
                        p.title + "：用例输入必须写成单行（兼容命令行参数式沙箱）");
                require(!c.output.isEmpty(), p.title + "：存在空输出用例");
                require(c.output.equals(normalize(c.output)), p.title + "：用例输出存在多余换行");
            }
        }
        out.println("字段长度与格式检查通过");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    // ------------------------------------------------------------------
    // 本地校验：编译参考答案并跑全部用例
    // ------------------------------------------------------------------

    private static void verifyAll(List<Problem> problems) throws Exception {
        List<String> failures = new ArrayList<>();
        int cases = 0;
        for (Problem p : problems) {
            Path dir = Files.createTempDirectory("ojseed-");
            try {
                Path javaFile = dir.resolve("Main.java");
                Files.writeString(javaFile, p.javaCode, StandardCharsets.UTF_8);
                run(dir, "javac", "-encoding", "UTF-8", javaFile.toString());

                Path cppFile = dir.resolve("main.cpp");
                Files.writeString(cppFile, p.cppCode, StandardCharsets.UTF_8);
                Path cppExe = dir.resolve("main.exe");
                run(dir, "g++", "-O2", "-std=c++17", "-o", cppExe.toString(), cppFile.toString());

                for (int i = 0; i < p.cases.size(); i++) {
                    Case c = p.cases.get(i);
                    cases++;
                    String javaIn = runWithInput(dir, javaRun(dir, false, c.input), c.input);
                    String cppIn = runWithInput(dir, cppRun(cppExe, false, c.input), c.input);
                    failures.addAll(compare(p, i, c, "java-stdin", javaIn));
                    failures.addAll(compare(p, i, c, "cpp-stdin", cppIn));
                    // 命令行参数方式：每个用例都验证一遍
                    String javaArgs = run(dir, javaRun(dir, true, c.input));
                    String cppArgs = run(dir, cppRun(cppExe, true, c.input));
                    failures.addAll(compare(p, i, c, "java-args", javaArgs));
                    failures.addAll(compare(p, i, c, "cpp-args", cppArgs));
                }
                if (failures.isEmpty()) {
                    out.println("校验通过  " + p.title + "  (" + p.cases.size() + " 个用例 x Java/C++ x 两种传参方式)");
                }
            } finally {
                deleteRecursively(dir);
            }
        }
        if (!failures.isEmpty()) {
            out.println("校验失败，未写入数据库：");
            failures.forEach(f -> out.println("  - " + f));
            System.exit(1);
        }
        out.println("全部参考代码与测试用例校验通过，共 " + cases + " 个用例");
    }

    private static List<String> compare(Problem p, int index, Case c, String mode, String actual) {
        List<String> failures = new ArrayList<>();
        String normalized = normalize(actual);
        if (!c.output.equals(normalized)) {
            failures.add(p.title + " 用例#" + (index + 1) + " [" + mode + "] 期望 <" + c.output
                    + "> 实际 <" + normalized + ">");
        }
        return failures;
    }

    private static String normalize(String text) {
        String t = text.replace("\r\n", "\n").replace("\r", "\n");
        String[] lines = t.split("\n", -1);
        int end = lines.length;
        while (end > 0 && lines[end - 1].isEmpty()) {
            end--;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < end; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        return sb.toString();
    }

    private static List<String> javaRun(Path dir, boolean useArgs, String input) {
        List<String> cmd = new ArrayList<>(Arrays.asList("java", "-Dfile.encoding=UTF-8", "-cp",
                dir.toAbsolutePath().toString(), "Main"));
        if (useArgs) {
            cmd.addAll(Arrays.asList(input.split(" ")));
        }
        return cmd;
    }

    private static List<String> cppRun(Path exe, boolean useArgs, String input) {
        List<String> cmd = new ArrayList<>();
        cmd.add(exe.toAbsolutePath().toString());
        if (useArgs) {
            cmd.addAll(Arrays.asList(input.split(" ")));
        }
        return cmd;
    }

    private static String run(Path dir, List<String> command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        return run(pb);
    }

    private static String run(Path dir, String... command) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        return run(pb);
    }

    private static String run(ProcessBuilder pb) throws Exception {
        Process process = pb.start();
        try (OutputStream stdin = process.getOutputStream()) {
            // 用例输入通过标准输入写入；参数方式下程序不读 stdin，关闭即可
        }
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return "<执行超时>";
        }
        String text;
        try (InputStream in = process.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (process.exitValue() != 0) {
            return text + "\n<退出码 " + process.exitValue() + ">";
        }
        return text;
    }

    private static String runWithInput(Path dir, List<String> command, String input) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.directory(dir.toFile());
        pb.redirectErrorStream(true);
        Process process = pb.start();
        try (OutputStream stdin = process.getOutputStream()) {
            stdin.write(input.getBytes(StandardCharsets.UTF_8));
            stdin.write('\n');
        }
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return "<执行超时>";
        }
        String text;
        try (InputStream in = process.getInputStream()) {
            text = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        if (process.exitValue() != 0) {
            return text + "\n<退出码 " + process.exitValue() + ">";
        }
        return text;
    }

    private static void deleteRecursively(Path dir) throws Exception {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (var stream = Files.walk(dir)) {
            stream.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (Exception ignored) {
                    // 临时文件清理失败不影响结果
                }
            });
        }
    }

    // ------------------------------------------------------------------
    // 写库
    // ------------------------------------------------------------------

    private static final String INSERT_SQL = "INSERT INTO question "
            + "(title, content, tags, answer, submitNum, acceptedNum, judgeCase, judgeConfig, "
            + "thumbNum, favourNum, userId, createTime, updateTime, isDelete) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, 0, ?, NOW(), NOW(), 0)";

    private static void seed(List<Problem> problems, boolean replace) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            conn.setAutoCommit(false);
            int inserted = 0;
            int skipped = 0;
            for (Problem p : problems) {
                Long existingId = findId(conn, p.title);
                if (existingId != null && !replace) {
                    p.id = existingId;
                    skipped++;
                    out.println("已存在，跳过  " + p.title + " (id=" + existingId + ")");
                    continue;
                }
                if (existingId != null) {
                    try (PreparedStatement ps = conn.prepareStatement("DELETE FROM question WHERE id = ?")) {
                        ps.setLong(1, existingId);
                        ps.executeUpdate();
                    }
                    out.println("已删除旧记录  " + p.title + " (id=" + existingId + ")");
                }
                try (PreparedStatement ps = conn.prepareStatement(INSERT_SQL, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setString(1, p.title);
                    ps.setString(2, p.content);
                    ps.setString(3, p.tagsJson);
                    ps.setString(4, p.answer);
                    ps.setInt(5, p.submitNum);
                    ps.setInt(6, p.acceptedNum);
                    ps.setString(7, p.judgeCaseJson);
                    ps.setString(8, p.judgeConfigJson);
                    ps.setLong(9, CREATOR_ID);
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        if (rs.next()) {
                            p.id = rs.getLong(1);
                        }
                    }
                }
                inserted++;
                out.println("写入成功  id=" + p.id + "  " + p.title + "  (" + p.cases.size() + " 个用例)");
            }
            conn.commit();
            out.println("本次写入 " + inserted + " 题，跳过 " + skipped + " 题");
        }
    }

    private static Long findId(Connection conn, String title) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT id FROM question WHERE title = ? AND isDelete = 0 ORDER BY id LIMIT 1")) {
            ps.setString(1, title);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : null;
            }
        }
    }

    /** --check-only 模式：只按标题找回已有题目的 id，不写库 */
    private static void loadExistingIds(List<Problem> problems) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            for (Problem p : problems) {
                p.id = findId(conn, p.title);
                if (p.id == null) {
                    out.println("库中不存在  " + p.title);
                }
            }
        }
    }

    /**
     * 用后端自己的 DTO（JudgeCase / JudgeConfig）和 Hutool 解析库中 JSON，
     * 等价于后端 QuestionVO.objToVo 与 JudgeServiceImpl 的读取方式。
     * 需要把 target/classes 和 hutool 加进 classpath，找不到时自动跳过。
     */
    private static void verifyWithProjectDto() throws Exception {
        Class<?> caseClass;
        Class<?> configClass;
        Method toList;
        Method toBean;
        try {
            caseClass = Class.forName("com.yupi.yuoj.model.dto.question.JudgeCase");
            configClass = Class.forName("com.yupi.yuoj.model.dto.question.JudgeConfig");
            Class<?> jsonUtil = Class.forName("cn.hutool.json.JSONUtil");
            toList = jsonUtil.getMethod("toList", String.class, Class.class);
            toBean = jsonUtil.getMethod("toBean", String.class, Class.class);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            out.println("未找到项目 DTO 或 Hutool，跳过 DTO 解析校验");
            return;
        }

        Method getInput = caseClass.getMethod("getInput");
        Method getOutput = caseClass.getMethod("getOutput");
        Method getTimeLimit = configClass.getMethod("getTimeLimit");
        Method getMemoryLimit = configClass.getMethod("getMemoryLimit");
        Method getStackLimit = configClass.getMethod("getStackLimit");

        List<String> errors = new ArrayList<>();
        int problems = 0;
        int cases = 0;
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id, title, tags, judgeCase, judgeConfig FROM question "
                     + "WHERE isDelete = 0 ORDER BY id")) {
            while (rs.next()) {
                long id = rs.getLong("id");
                String title = rs.getString("title");
                List<?> tagList = (List<?>) toList.invoke(null, rs.getString("tags"), String.class);
                List<?> caseList = (List<?>) toList.invoke(null, rs.getString("judgeCase"), caseClass);
                Object config = toBean.invoke(null, rs.getString("judgeConfig"), configClass);

                if (tagList == null || tagList.isEmpty()) {
                    errors.add(id + " " + title + "：标签解析为空");
                }
                if (caseList == null || caseList.isEmpty()) {
                    errors.add(id + " " + title + "：判题用例解析为空");
                } else {
                    for (Object one : caseList) {
                        Object input = getInput.invoke(one);
                        Object output = getOutput.invoke(one);
                        if (input == null || output == null) {
                            errors.add(id + " " + title + "：用例 input/output 存在 null");
                            break;
                        }
                    }
                    cases += caseList.size();
                }
                if (config == null || getTimeLimit.invoke(config) == null || getMemoryLimit.invoke(config) == null
                        || getStackLimit.invoke(config) == null) {
                    errors.add(id + " " + title + "：判题配置解析失败");
                }
                problems++;
            }
        }
        if (errors.isEmpty()) {
            out.println("DTO 解析校验通过：" + problems + " 题 / " + cases + " 个用例可被后端正常读取");
        } else {
            out.println("DTO 解析校验发现问题：");
            errors.forEach(e -> out.println("  - " + e));
        }
    }

    private static void postCheck(List<Problem> problems) throws Exception {
        try (Connection conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            List<String> errors = new ArrayList<>();
            for (Problem p : problems) {
                if (p.id == null) {
                    continue;
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT title, content, tags, answer, judgeCase, judgeConfig, userId, submitNum, acceptedNum "
                                + "FROM question WHERE id = ?")) {
                    ps.setLong(1, p.id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            errors.add(p.title + "：写入后读不到记录");
                            continue;
                        }
                        if (!p.title.equals(rs.getString("title"))) {
                            errors.add(p.title + "：标题回读不一致");
                        }
                        if (!p.content.equals(rs.getString("content"))) {
                            errors.add(p.title + "：题面回读不一致（编码问题？）");
                        }
                        if (!p.answer.equals(rs.getString("answer"))) {
                            errors.add(p.title + "：答案回读不一致");
                        }
                        if (!p.tagsJson.equals(rs.getString("tags"))) {
                            errors.add(p.title + "：标签回读不一致");
                        }
                        if (!p.judgeCaseJson.equals(rs.getString("judgeCase"))) {
                            errors.add(p.title + "：判题用例回读不一致");
                        }
                        if (!p.judgeConfigJson.equals(rs.getString("judgeConfig"))) {
                            errors.add(p.title + "：判题配置回读不一致");
                        }
                        if (rs.getLong("userId") != CREATOR_ID) {
                            errors.add(p.title + "：创建用户不一致");
                        }
                    }
                }
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM question WHERE JSON_VALID(tags) = 0 "
                         + "OR JSON_VALID(judgeCase) = 0 OR JSON_VALID(judgeConfig) = 0")) {
                rs.next();
                int invalid = rs.getInt(1);
                if (invalid > 0) {
                    errors.add("存在 " + invalid + " 条 JSON 字段非法的记录");
                }
            }
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM question WHERE isDelete = 0")) {
                rs.next();
                out.println("当前题库题目总数：" + rs.getInt(1));
            }
            if (errors.isEmpty()) {
                out.println("数据库回读校验通过（题面/答案/标签/用例/配置完全一致，JSON 合法）");
            } else {
                out.println("数据库回读校验发现问题：");
                errors.forEach(e -> out.println("  - " + e));
            }
        }
    }

    // ------------------------------------------------------------------
    // 题库内容
    // ------------------------------------------------------------------

    private static List<Problem> problems() {
        List<Problem> list = new ArrayList<>();

        list.add(p("A+B 问题",
                new String[]{"入门", "数学", "简单"},
                """
                输入两个整数 a 和 b，输出它们的和。""",
                """
                一行，包含两个整数 a 和 b（-10^9 ≤ a, b ≤ 10^9），两个整数之间用一个空格分隔。""",
                """
                一行，一个整数，表示 a + b 的结果。""",
                """
                结果可能超出 int 的范围，请使用 64 位整数（Java 的 long、C++ 的 long long）进行计算。""",
                """
                读入两个 64 位整数，相加后直接输出。""",
                "时间复杂度 O(1)，空间复杂度 O(1)。",
                new Case[]{
                        new Case("1 2", "3"),
                        new Case("100 -50", "50"),
                        new Case("-7 -8", "-15"),
                        new Case("0 0", "0"),
                        new Case("1000000000 1000000000", "2000000000"),
                        new Case("-1000000000 -1000000000", "-2000000000")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        long a = sc.nextLong();
                        long b = sc.nextLong();
                        System.out.println(a + b);
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    long long a, b;
                    in >> a >> b;
                    cout << a + b << endl;
                    return 0;
                }""",
                520, 436, 3000));

        list.add(p("两数之和",
                new String[]{"数组", "哈希表", "简单"},
                """
                给定一个长度为 n 的整数数组 nums 和一个目标值 target，请找出数组中两个不同的位置 i、j（i < j），
                使得 nums[i] + nums[j] = target，并输出 i 和 j。数据保证至多存在一组满足条件的下标对。
                如果不存在这样的两个数，输出 -1。""",
                """
                第一行两个整数 n 和 target（2 ≤ n ≤ 10^5，-10^9 ≤ target ≤ 10^9）。
                第二行 n 个整数 nums[i]（-10^9 ≤ nums[i] ≤ 10^9）。""",
                """
                一行，两个整数 i 和 j（i < j，下标从 0 开始，用空格分隔）；不存在时输出 -1。""",
                """
                答案唯一，不需要考虑多组答案的取舍；两数之和可能超出 int 范围，建议用 64 位整数比较。""",
                """
                用哈希表记录已经遍历过的数值及其下标。遍历到 nums[i] 时，若 target - nums[i] 已经出现过，
                就找到了答案，否则把 nums[i] 和它的下标存入哈希表，时间复杂度 O(n)。""",
                "时间复杂度 O(n)，空间复杂度 O(n)。",
                new Case[]{
                        new Case("5 9 2 7 11 15 1", "0 1"),
                        new Case("4 6 3 2 4 1", "1 2"),
                        new Case("6 0 -3 4 3 90 5 8", "0 2"),
                        new Case("2 6 3 3", "0 1"),
                        new Case("4 100 30 70 41 60", "0 1"),
                        new Case("3 10 1 2 3", "-1")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        int n = sc.nextInt();
                        long target = sc.nextLong();
                        long[] nums = new long[n];
                        for (int i = 0; i < n; i++) {
                            nums[i] = sc.nextLong();
                        }
                        Map<Long, Integer> seen = new HashMap<>();
                        for (int i = 0; i < n; i++) {
                            Integer j = seen.get(target - nums[i]);
                            if (j != null) {
                                System.out.println(j + " " + i);
                                return;
                            }
                            seen.putIfAbsent(nums[i], i);
                        }
                        System.out.println(-1);
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    int n;
                    long long target;
                    in >> n >> target;
                    vector<long long> nums(n);
                    for (int i = 0; i < n; i++) {
                        in >> nums[i];
                    }
                    unordered_map<long long, int> seen;
                    for (int i = 0; i < n; i++) {
                        auto it = seen.find(target - nums[i]);
                        if (it != seen.end()) {
                            cout << it->second << " " << i << endl;
                            return 0;
                        }
                        seen.emplace(nums[i], i);
                    }
                    cout << -1 << endl;
                    return 0;
                }""",
                380, 232, 3000));

        list.add(p("回文数",
                new String[]{"数学", "字符串", "简单"},
                """
                判断给定的整数 n 是否为回文数。回文数是指正序读和倒序读结果相同的整数，例如 121、0 是回文数，
                10、-121 不是回文数（负数一律不是回文数）。""",
                """
                一行，一个整数 n（-2^31 ≤ n ≤ 2^31-1）。""",
                """
                一行，如果 n 是回文数输出 true，否则输出 false。""",
                """
                负数的负号无法被对称匹配，因此一律输出 false；正数可以转成字符串后比较首尾字符。""",
                """
                先处理负数。正数转成字符串后使用双指针从两端向中间比较，任意一对字符不同就输出 false。
                也可以只反转数字的后一半做比较，好处是不占用额外字符串空间。""",
                "时间复杂度 O(数字位数)，空间复杂度 O(数字位数)（字符串解法）。",
                new Case[]{
                        new Case("121", "true"),
                        new Case("-121", "false"),
                        new Case("10", "false"),
                        new Case("0", "true"),
                        new Case("12321", "true"),
                        new Case("2147447412", "true")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        long n = sc.nextLong();
                        if (n < 0) {
                            System.out.println(false);
                            return;
                        }
                        String s = String.valueOf(n);
                        int i = 0;
                        int j = s.length() - 1;
                        while (i < j) {
                            if (s.charAt(i) != s.charAt(j)) {
                                System.out.println(false);
                                return;
                            }
                            i++;
                            j--;
                        }
                        System.out.println(true);
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    long long n;
                    in >> n;
                    if (n < 0) {
                        cout << "false" << endl;
                        return 0;
                    }
                    string s = to_string(n);
                    string r(s.rbegin(), s.rend());
                    cout << (s == r ? "true" : "false") << endl;
                    return 0;
                }""",
                420, 312, 3000));

        list.add(p("求阶乘",
                new String[]{"数学", "循环", "简单"},
                """
                计算非负整数 n 的阶乘 n!。规定 0! = 1，n! = 1 × 2 × … × n。""",
                """
                一行，一个整数 n（0 ≤ n ≤ 20）。""",
                """
                一行，一个整数，表示 n! 的值。""",
                """
                20! = 2432902008176640000，不会超出 64 位整数范围，但会超出 int，必须用 long（C++ 为 long long）。""",
                """
                从 1 累乘到 n 即可，注意 0 的阶乘为 1，循环不执行时结果仍为 1。""",
                "时间复杂度 O(n)，空间复杂度 O(1)。",
                new Case[]{
                        new Case("0", "1"),
                        new Case("1", "1"),
                        new Case("5", "120"),
                        new Case("10", "3628800"),
                        new Case("15", "1307674368000"),
                        new Case("20", "2432902008176640000")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        int n = sc.nextInt();
                        long result = 1;
                        for (int i = 2; i <= n; i++) {
                            result *= i;
                        }
                        System.out.println(result);
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    int n;
                    in >> n;
                    long long result = 1;
                    for (int i = 2; i <= n; i++) {
                        result *= i;
                    }
                    cout << result << endl;
                    return 0;
                }""",
                350, 286, 3000));

        list.add(p("最大公约数",
                new String[]{"数学", "数论", "简单"},
                """
                给定两个正整数 a 和 b，求它们的最大公约数（gcd）。""",
                """
                一行，两个正整数 a 和 b（1 ≤ a, b ≤ 10^18），用一个空格分隔。""",
                """
                一行，一个整数，表示 a 和 b 的最大公约数。""",
                """
                请使用辗转相除法，数据范围较大，循环时要使用 64 位整数。""",
                """
                辗转相除：gcd(a, b) = gcd(b, a mod b)，当 b 为 0 时 a 就是答案。""",
                "时间复杂度 O(log min(a, b))，空间复杂度 O(1)。",
                new Case[]{
                        new Case("12 18", "6"),
                        new Case("17 5", "1"),
                        new Case("100 100", "100"),
                        new Case("1071 462", "21"),
                        new Case("360 240", "120"),
                        new Case("1000000007 998244353", "1")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        long a = sc.nextLong();
                        long b = sc.nextLong();
                        while (b != 0) {
                            long t = a % b;
                            a = b;
                            b = t;
                        }
                        System.out.println(a);
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    long long a, b;
                    in >> a >> b;
                    while (b != 0) {
                        long long t = a % b;
                        a = b;
                        b = t;
                    }
                    cout << a << endl;
                    return 0;
                }""",
                310, 252, 3000));

        list.add(p("素数判定",
                new String[]{"数学", "数论", "中等"},
                """
                判断整数 n 是否为素数。素数是指大于 1、且只能被 1 和自身整除的整数。""",
                """
                一行，一个整数 n（2 ≤ n ≤ 10^12）。""",
                """
                一行，如果 n 是素数输出 yes，否则输出 no。""",
                """
                只需要试除到 √n。循环条件建议写成 i <= n / i，避免 i * i 溢出。""",
                """
                枚举 i 从 2 到 √n，如果 n 能被 i 整除就说明不是素数；枚举完都没有因子则为素数，
                时间复杂度 O(√n)，最大约 10^6 次循环。""",
                "时间复杂度 O(√n)，空间复杂度 O(1)。",
                new Case[]{
                        new Case("2", "yes"),
                        new Case("4", "no"),
                        new Case("97", "yes"),
                        new Case("1000000006", "no"),
                        new Case("1000000007", "yes"),
                        new Case("999999937", "yes")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        long n = sc.nextLong();
                        if (n < 2) {
                            System.out.println("no");
                            return;
                        }
                        for (long i = 2; i <= n / i; i++) {
                            if (n % i == 0) {
                                System.out.println("no");
                                return;
                            }
                        }
                        System.out.println("yes");
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    long long n;
                    in >> n;
                    if (n < 2) {
                        cout << "no" << endl;
                        return 0;
                    }
                    for (long long i = 2; i <= n / i; i++) {
                        if (n % i == 0) {
                            cout << "no" << endl;
                            return 0;
                        }
                    }
                    cout << "yes" << endl;
                    return 0;
                }""",
                330, 196, 5000));

        list.add(p("数组求和与最大值",
                new String[]{"数组", "循环", "简单"},
                """
                给定 n 个整数，求它们的总和与最大值。""",
                """
                第一行一个整数 n（1 ≤ n ≤ 10^5）；第二行 n 个整数 a_i（-10^9 ≤ a_i ≤ 10^9）。""",
                """
                一行，两个整数，用空格分隔，依次为所有元素的和与最大值。""",
                """
                和可能超出 int 范围，请用 64 位整数累加；最大值可以初始化为很小的数，或直接用第一个元素初始化。""",
                """
                一边读入一边累加求和，同时用 max 维护最大值即可，只需要一次遍历。""",
                "时间复杂度 O(n)，空间复杂度 O(1)。",
                new Case[]{
                        new Case("5 1 2 3 4 5", "15 5"),
                        new Case("1 -100", "-100 -100"),
                        new Case("6 -5 -2 -9 -1 -7 -3", "-27 -1"),
                        new Case("3 0 0 0", "0 0"),
                        new Case("4 1000000000 1000000000 1000000000 1000000000", "4000000000 1000000000")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        int n = sc.nextInt();
                        long sum = 0;
                        long max = Long.MIN_VALUE;
                        for (int i = 0; i < n; i++) {
                            long v = sc.nextLong();
                            sum += v;
                            max = Math.max(max, v);
                        }
                        System.out.println(sum + " " + max);
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    int n;
                    in >> n;
                    long long sum = 0;
                    long long mx = LLONG_MIN;
                    for (int i = 0; i < n; i++) {
                        long long v;
                        in >> v;
                        sum += v;
                        mx = max(mx, v);
                    }
                    cout << sum << " " << mx << endl;
                    return 0;
                }""",
                270, 224, 3000));

        list.add(p("字符串反转",
                new String[]{"字符串", "双指针", "简单"},
                """
                给定一个不含空格的字符串 s，输出将它反转后的字符串。""",
                """
                一行，一个不含空格的字符串 s（1 ≤ |s| ≤ 1000，只包含大小写字母和数字）。""",
                """
                一行，反转后的字符串。""",
                """
                可以从最后一个字符往前遍历拼接，也可以把字符串转成字符数组后用双指针交换。""",
                """
                使用双指针：左指针从 0 开始，右指针从末尾开始，交换后向中间靠拢，交换到相遇为止。""",
                "时间复杂度 O(n)，空间复杂度 O(n)（存放字符数组）。",
                new Case[]{
                        new Case("hello", "olleh"),
                        new Case("abc123", "321cba"),
                        new Case("a", "a"),
                        new Case("AbCdEf", "fEdCbA"),
                        new Case("123454321", "123454321")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        String s = sc.next();
                        char[] arr = s.toCharArray();
                        int i = 0;
                        int j = arr.length - 1;
                        while (i < j) {
                            char t = arr[i];
                            arr[i] = arr[j];
                            arr[j] = t;
                            i++;
                            j--;
                        }
                        System.out.println(new String(arr));
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    string s;
                    in >> s;
                    reverse(s.begin(), s.end());
                    cout << s << endl;
                    return 0;
                }""",
                300, 262, 3000));

        list.add(p("数组升序排序",
                new String[]{"排序", "数组", "简单"},
                """
                给定 n 个整数，请把它们按从小到大的顺序输出。""",
                """
                第一行一个整数 n（1 ≤ n ≤ 1000）；第二行 n 个整数 a_i（-10^9 ≤ a_i ≤ 10^9）。""",
                """
                一行，n 个整数，按升序排列，相邻两个整数之间用一个空格分隔。""",
                """
                可以使用语言自带的排序函数，也可以自己实现冒泡排序、插入排序、快速排序等。""",
                """
                直接调用库排序（Java 的 Arrays.sort、C++ 的 std::sort）即可，注意数据范围用 64 位整数保存。""",
                "时间复杂度 O(n log n)，空间复杂度 O(n)（存放数组）。",
                new Case[]{
                        new Case("5 3 1 4 1 5", "1 1 3 4 5"),
                        new Case("1 -2", "-2"),
                        new Case("6 5 4 3 2 1 0", "0 1 2 3 4 5"),
                        new Case("7 -1 0 -1 2 -3 2 0", "-3 -1 -1 0 0 2 2"),
                        new Case("3 1000000000 -1000000000 0", "-1000000000 0 1000000000")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        int n = sc.nextInt();
                        long[] arr = new long[n];
                        for (int i = 0; i < n; i++) {
                            arr[i] = sc.nextLong();
                        }
                        Arrays.sort(arr);
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < n; i++) {
                            if (i > 0) {
                                sb.append(' ');
                            }
                            sb.append(arr[i]);
                        }
                        System.out.println(sb);
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    int n;
                    in >> n;
                    vector<long long> arr(n);
                    for (int i = 0; i < n; i++) {
                        in >> arr[i];
                    }
                    sort(arr.begin(), arr.end());
                    for (int i = 0; i < n; i++) {
                        if (i > 0) cout << " ";
                        cout << arr[i];
                    }
                    cout << endl;
                    return 0;
                }""",
                290, 218, 3000));

        list.add(p("二分查找第一次出现的位置",
                new String[]{"数组", "二分查找", "中等"},
                """
                给定一个长度为 n 的非降序整数数组和一个目标值 target，请找出 target 在数组中第一次出现的下标
                （下标从 0 开始）。如果数组中不存在 target，输出 -1。""",
                """
                第一行一个整数 n（1 ≤ n ≤ 10^5）；第二行 n 个非降序排列的整数 a_i（-10^9 ≤ a_i ≤ 10^9）；
                第三行一个整数 target（-10^9 ≤ target ≤ 10^9）。""",
                """
                一行，target 第一次出现的下标；不存在时输出 -1。""",
                """
                要求使用 O(log n) 的二分查找，找到等于 target 的位置后继续向左收缩区间，才能保证是第一次出现。""",
                """
                二分左边界：用 l、r 维护候选区间，当 a[mid] >= target 时令 r = mid，否则 l = mid + 1，
                循环结束后判断 a[l] 是否等于 target。""",
                "时间复杂度 O(log n)，空间复杂度 O(n)（读入数组）。",
                new Case[]{
                        new Case("5 1 2 3 3 5 3", "2"),
                        new Case("1 7 7", "0"),
                        new Case("4 1 2 3 4 9", "-1"),
                        new Case("6 -5 -3 -1 0 2 4 0", "3"),
                        new Case("3 2 2 2 2", "0")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        int n = sc.nextInt();
                        long[] arr = new long[n];
                        for (int i = 0; i < n; i++) {
                            arr[i] = sc.nextLong();
                        }
                        long target = sc.nextLong();
                        int left = 0;
                        int right = n - 1;
                        while (left < right) {
                            int mid = (left + right) / 2;
                            if (arr[mid] >= target) {
                                right = mid;
                            } else {
                                left = mid + 1;
                            }
                        }
                        System.out.println(arr[left] == target ? left : -1);
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    int n;
                    in >> n;
                    vector<long long> arr(n);
                    for (int i = 0; i < n; i++) {
                        in >> arr[i];
                    }
                    long long target;
                    in >> target;
                    int left = 0;
                    int right = n - 1;
                    while (left < right) {
                        int mid = (left + right) / 2;
                        if (arr[mid] >= target) {
                            right = mid;
                        } else {
                            left = mid + 1;
                        }
                    }
                    cout << (arr[left] == target ? left : -1) << endl;
                    return 0;
                }""",
                250, 168, 3000));

        list.add(p("斐波那契数列",
                new String[]{"递推", "数学", "简单"},
                """
                斐波那契数列定义为 F(0) = 0，F(1) = 1，F(n) = F(n-1) + F(n-2)（n ≥ 2），
                给定 n，求 F(n)。""",
                """
                一行，一个整数 n（0 ≤ n ≤ 90）。""",
                """
                一行，一个整数，表示 F(n)。""",
                """
                F(90) = 2880067194370816120，请使用 64 位整数保存结果，不要使用递归（会重复计算超时）。""",
                """
                用两个变量滚动递推，从 F(0)、F(1) 推到 F(n) 即可，避免递归带来的指数级重复计算。""",
                "时间复杂度 O(n)，空间复杂度 O(1)。",
                new Case[]{
                        new Case("0", "0"),
                        new Case("1", "1"),
                        new Case("10", "55"),
                        new Case("30", "832040"),
                        new Case("50", "12586269025"),
                        new Case("90", "2880067194370816120")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        int n = sc.nextInt();
                        long a = 0;
                        long b = 1;
                        for (int i = 0; i < n; i++) {
                            long next = a + b;
                            a = b;
                            b = next;
                        }
                        System.out.println(a);
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    int n;
                    in >> n;
                    long long a = 0;
                    long long b = 1;
                    for (int i = 0; i < n; i++) {
                        long long next = a + b;
                        a = b;
                        b = next;
                    }
                    cout << a << endl;
                    return 0;
                }""",
                320, 258, 3000));

        list.add(p("字符类型统计",
                new String[]{"字符串", "遍历", "简单"},
                """
                给定一个不含空格的字符串，统计其中数字字符、字母字符和其他字符的个数。""",
                """
                一行，一个不含空格的字符串 s（1 ≤ |s| ≤ 1000）。""",
                """
                一行，三个整数，用空格分隔，依次为数字字符个数、字母字符个数、其他字符个数。""",
                """
                Java 可用 Character.isDigit 和 Character.isLetter 判断；C++ 用 isdigit、isalpha，
                注意它们只应传入非负字符，推荐先转换成 unsigned char。""",
                """
                遍历字符串的每个字符，分别统计三类字符的个数，最后按顺序输出。""",
                "时间复杂度 O(n)，空间复杂度 O(1)。",
                new Case[]{
                        new Case("abc123!@#", "3 3 3"),
                        new Case("A1", "1 1 0"),
                        new Case("hello", "0 5 0"),
                        new Case("2024-09-11", "8 0 2"),
                        new Case("Zz9?", "1 2 1")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        String s = sc.next();
                        int digits = 0;
                        int letters = 0;
                        int others = 0;
                        for (int i = 0; i < s.length(); i++) {
                            char c = s.charAt(i);
                            if (Character.isDigit(c)) {
                                digits++;
                            } else if (Character.isLetter(c)) {
                                letters++;
                            } else {
                                others++;
                            }
                        }
                        System.out.println(digits + " " + letters + " " + others);
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    string s;
                    in >> s;
                    int digits = 0;
                    int letters = 0;
                    int others = 0;
                    for (char ch : s) {
                        unsigned char c = (unsigned char) ch;
                        if (isdigit(c)) {
                            digits++;
                        } else if (isalpha(c)) {
                            letters++;
                        } else {
                            others++;
                        }
                    }
                    cout << digits << " " << letters << " " << others << endl;
                    return 0;
                }""",
                230, 186, 3000));

        list.add(p("整数平方根",
                new String[]{"数学", "二分查找", "中等"},
                """
                给定一个非负整数 n，求 ⌊√n⌋，即 n 的算术平方根的整数部分（向下取整）。""",
                """
                一行，一个整数 n（0 ≤ n ≤ 10^18）。""",
                """
                一行，一个整数，表示 ⌊√n⌋。""",
                """
                直接用 Math.sqrt 得到 double 结果可能因为精度不足差 1，建议对结果做一次修正，
                或者用二分查找在 [0, 10^9] 中找最大的 mid 满足 mid * mid <= n。""",
                """
                先用 Math.sqrt 得到一个近似值，再分别用乘法和除法做上下修正，保证 mid * mid <= n < (mid+1) * (mid+1)。
                也可以直接二分答案，用 n / mid >= mid 判断避免乘法溢出。""",
                "时间复杂度 O(1)（修正法）或 O(log n)（二分），空间复杂度 O(1)。",
                new Case[]{
                        new Case("0", "0"),
                        new Case("1", "1"),
                        new Case("15", "3"),
                        new Case("16", "4"),
                        new Case("999999999999999999", "999999999"),
                        new Case("1000000000000000000", "1000000000")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        long n = sc.nextLong();
                        if (n <= 1) {
                            System.out.println(n);
                            return;
                        }
                        long r = (long) Math.sqrt((double) n);
                        while ((r + 1) <= n / (r + 1)) {
                            r++;
                        }
                        while (r > n / r) {
                            r--;
                        }
                        System.out.println(r);
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    unsigned long long n;
                    in >> n;
                    unsigned long long low = 0;
                    unsigned long long high = 1000000000ULL;
                    while (low < high) {
                        unsigned long long mid = (low + high + 1) / 2;
                        if (mid <= n / mid) {
                            low = mid;
                        } else {
                            high = mid - 1;
                        }
                    }
                    cout << low << endl;
                    return 0;
                }""",
                210, 152, 3000));

        list.add(p("十进制转二进制",
                new String[]{"进制转换", "数学", "简单"},
                """
                给定一个正整数 n，输出它的二进制表示（不含前导零）。""",
                """
                一行，一个整数 n（1 ≤ n ≤ 10^18）。""",
                """
                一行，n 的二进制表示。""",
                """
                不断对 2 取余、再除以 2，把得到的余数倒序拼接即可；语言自带的转换函数也可以直接使用。""",
                """
                每次取出最低位放入结果，然后右移一位，最后把结果反转；或者直接调用 Java 的
                Long.toBinaryString，C++ 可以用 bitset 或手写循环（数据范围到 10^18，需要 64 位整数）。""",
                "时间复杂度 O(log n)，空间复杂度 O(log n)。",
                new Case[]{
                        new Case("1", "1"),
                        new Case("2", "10"),
                        new Case("10", "1010"),
                        new Case("255", "11111111"),
                        new Case("1000000", "11110100001001000000"),
                        new Case("1099511627776", "10000000000000000000000000000000000000000")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        long n = sc.nextLong();
                        if (n == 0) {
                            System.out.println(0);
                            return;
                        }
                        StringBuilder sb = new StringBuilder();
                        while (n > 0) {
                            sb.append(n & 1);
                            n >>= 1;
                        }
                        System.out.println(sb.reverse());
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    unsigned long long n;
                    in >> n;
                    if (n == 0) {
                        cout << 0 << endl;
                        return 0;
                    }
                    string s;
                    while (n > 0) {
                        s += char('0' + (n & 1));
                        n >>= 1;
                    }
                    reverse(s.begin(), s.end());
                    cout << s << endl;
                    return 0;
                }""",
                270, 204, 3000));

        list.add(p("括号匹配",
                new String[]{"栈", "字符串", "中等"},
                """
                给定一个只包含圆括号、方括号和花括号的字符串，判断其中的括号是否合法。合法的条件是：
                每个左括号都能在后面找到一个类型相同的右括号一一匹配，且括号之间不能交叉嵌套（例如 ([)] 不合法）。""",
                """
                一行，一个只包含 ()[]{} 六种字符的字符串 s（1 ≤ |s| ≤ 1000）。""",
                """
                一行，如果括号匹配输出 yes，否则输出 no。""",
                """
                用栈处理：遇到左括号就入栈；遇到右括号时，如果栈为空或栈顶不是对应的左括号就说明不合法；
                最后栈必须为空。""",
                """
                维护一个栈，遍历字符串：左括号入栈，右括号与栈顶匹配并出栈。遍历结束后栈为空则输出 yes，
                过程中出现不匹配、或者结束后栈非空则输出 no。""",
                "时间复杂度 O(n)，空间复杂度 O(n)。",
                new Case[]{
                        new Case("()", "yes"),
                        new Case("()[]{}", "yes"),
                        new Case("([{}])", "yes"),
                        new Case("(]", "no"),
                        new Case("(((", "no"),
                        new Case(")(", "no")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        String s = sc.next();
                        Deque<Character> stack = new ArrayDeque<>();
                        for (int i = 0; i < s.length(); i++) {
                            char c = s.charAt(i);
                            if (c == '(' || c == '[' || c == '{') {
                                stack.push(c);
                            } else {
                                if (stack.isEmpty()) {
                                    System.out.println("no");
                                    return;
                                }
                                char top = stack.pop();
                                if ((c == ')' && top != '(') || (c == ']' && top != '[') || (c == '}' && top != '{')) {
                                    System.out.println("no");
                                    return;
                                }
                            }
                        }
                        System.out.println(stack.isEmpty() ? "yes" : "no");
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    string s;
                    in >> s;
                    stack<char> st;
                    for (char c : s) {
                        if (c == '(' || c == '[' || c == '{') {
                            st.push(c);
                        } else {
                            if (st.empty()) {
                                cout << "no" << endl;
                                return 0;
                            }
                            char top = st.top();
                            st.pop();
                            if ((c == ')' && top != '(') || (c == ']' && top != '[') || (c == '}' && top != '{')) {
                                cout << "no" << endl;
                                return 0;
                            }
                        }
                    }
                    cout << (st.empty() ? "yes" : "no") << endl;
                    return 0;
                }""",
                240, 164, 3000));

        list.add(p("输出 1 到 n",
                new String[]{"循环", "入门", "简单"},
                """
                给定一个正整数 n，请把 1 到 n 依次输出，每个数字占一行。""",
                """
                一行，一个整数 n（1 ≤ n ≤ 100）。""",
                """
                共 n 行，第 i 行输出整数 i。""",
                """
                注意输出格式：每个数字独占一行，行末不要输出多余空格；换行符不要多输出也不能少输出。""",
                """
                循环 i 从 1 到 n，每轮输出 i 并换行即可。""",
                "时间复杂度 O(n)，空间复杂度 O(1)。",
                new Case[]{
                        new Case("1", "1"),
                        new Case("3", "1\n2\n3"),
                        new Case("5", "1\n2\n3\n4\n5"),
                        new Case("10", "1\n2\n3\n4\n5\n6\n7\n8\n9\n10")
                },
                """
                import java.util.*;

                public class Main {
                    public static void main(String[] args) {
                        Scanner sc = new Scanner(System.in);
                        if (args.length > 0) {
                            sc = new Scanner(String.join(" ", args));
                        }
                        int n = sc.nextInt();
                        for (int i = 1; i <= n; i++) {
                            System.out.println(i);
                        }
                    }
                }""",
                """
                #include <bits/stdc++.h>
                using namespace std;

                int main(int argc, char* argv[]) {
                    string argLine;
                    for (int i = 1; i < argc; i++) {
                        if (i > 1) argLine += " ";
                        argLine += argv[i];
                    }
                    istringstream argIn(argLine);
                    istream& in = (argc > 1) ? (istream&) argIn : cin;
                    int n;
                    in >> n;
                    for (int i = 1; i <= n; i++) {
                        cout << i << endl;
                    }
                    return 0;
                }""",
                190, 162, 3000));

        return list;
    }

}
