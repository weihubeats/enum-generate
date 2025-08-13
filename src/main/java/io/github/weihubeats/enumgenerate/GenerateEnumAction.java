package io.github.weihubeats.enumgenerate;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.javadoc.PsiDocComment;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;

/**
 * 根据字段 Javadoc 解析枚举常量并生成枚举类。
 * 支持格式(可混用)：0-待处理, 1:处理中, 2：完成, 3 = 失败, 4 - 已归档
 */
public class GenerateEnumAction extends AnAction {

    private static final Pattern ENTRY_PATTERN = Pattern.compile("(\\d+)\\s*[-:=：]\\s*(.+)");

    private static final Pattern TAIL_SUFFIX_PATTERN = Pattern.compile("(DO|DTO|VO|PO|POJO|Entity)$");

    private static final String NOTIFICATION_ID = "Enum Generator";




    @Override
    public void update(@NotNull AnActionEvent e) {
        // 控制 Action 的可见性
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        // 只有当选中的元素是 PsiField (类属性)时，才显示此 Action
        e.getPresentation().setEnabledAndVisible(psiElement instanceof PsiField);

    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        if (project == null || !(psiElement instanceof PsiField)) {
            notify(project, "未选中字段。", NotificationType.WARNING);
            return;
        }

        PsiField field = (PsiField) psiElement;
        PsiClass psiClass = field.getContainingClass();
        if (psiClass == null) {
            notify(project, "无法获取包含类。", NotificationType.ERROR);
            return;
        }

        PsiDocComment doc = field.getDocComment();
        if (doc == null) {
            notify(project, "该字段没有 Javadoc，无法解析枚举。", NotificationType.WARNING);
            return;
        }

        String rawJavadoc = doc.getText();
        List<EnumEntry> entries = parseEnumEntries(rawJavadoc);
        if (entries.isEmpty()) {
            notify(project, "Javadoc 中未解析到合法的枚举项。", NotificationType.WARNING);
            return;
        }

        // 生成类名
        String className = psiClass.getName() == null ? "Unknown" : psiClass.getName();
        String baseName = TAIL_SUFFIX_PATTERN.matcher(className).replaceAll("");
        String fieldName = field.getName();
        String enumClassName = buildEnumClassName(baseName, fieldName, psiClass);
        // 生成包名
        String packageName = getPackageName(psiClass);

        // 生成代码
        String enumCode = buildEnumSource(packageName, enumClassName, entries);

        // 写文件
        PsiDirectory dir = psiClass.getContainingFile().getContainingDirectory();
        writeEnumFile(project, dir, enumClassName, enumCode);
    }

    private String buildEnumClassName(String baseName, String fieldName, PsiClass contextClass) {
        String capitalizedField = fieldName.isEmpty()
            ? "Field"
            : Character.toUpperCase(fieldName.charAt(0)) + fieldName.substring(1);
        return baseName + capitalizedField + "Enum";
    }

    private String getPackageName(PsiClass psiClass) {
        String qName = psiClass.getQualifiedName();
        if (qName == null || !qName.contains(".")) {
            return "";
        }
        int lastDot = qName.lastIndexOf('.');
        return qName.substring(0, lastDot);
    }

    private List<EnumEntry> parseEnumEntries(String doc) {
        // 去除 /** */ 和行首 *
        String cleaned = Arrays.stream(
                doc.replaceAll("^/\\*\\*", "")
                    .replaceAll("\\*/$", "")
                    .split("\\R"))
            .map(line -> line.replaceFirst("^\\s*\\*", "").trim())
            .collect(Collectors.joining("\n"));

        // 按中英文逗号 / 换行切分
        String[] segments = cleaned.split("[,，\\n]+");
        List<EnumEntry> entries = new ArrayList<>();
        Set<Integer> usedCodes = new HashSet<>();

        for (String seg : segments) {
            String trimmed = seg.trim();
            if (trimmed.isEmpty()) continue;
            Matcher m = ENTRY_PATTERN.matcher(trimmed);
            if (!m.matches()) continue;
            int code;
            try {
                code = Integer.parseInt(m.group(1));
            } catch (NumberFormatException ex) {
                continue;
            }
            if (!usedCodes.add(code)) {
                // 重复 code，跳过或可追加策略
                continue;
            }
            String desc = m.group(2).trim();
            // 去掉尾部可能的句号/分号
            desc = desc.replaceAll("[;。．.]+$", "").trim();
            String enumName = deriveEnumConstantName(desc, code, entries.size());
            entries.add(new EnumEntry(enumName, code, desc));
        }
        return entries;
    }

    private String deriveEnumConstantName(String description, int code, int index) {
        // 只保留字母数字，其他转下划线
        String base = description.replaceAll("[^a-zA-Z0-9]+", "_");
        base = base.replaceAll("_+", "_");
        base = base.replaceAll("^_|_$", "");
        if (base.isEmpty()) {
            base = "C" + code;
        }
        if (!Character.isLetter(base.charAt(0))) {
            base = "C_" + base;
        }
        base = base.toUpperCase(Locale.ROOT);

        // 避免重复
        //（此处由调用方确保唯一；这里简单返回）
        return base;
    }

    private String buildEnumSource(String packageName, String enumName, List<EnumEntry> entries) {
        StringJoiner constantsJoiner = new StringJoiner(",\n");
        for (EnumEntry entry : entries) {
            constantsJoiner.add("    " + entry.name + "(" + entry.code + ", \"" + escape(entry.description) + "\")");
        }

        String pkgLine = packageName.isEmpty() ? "" : "package " + packageName + ";\n\n";

        return pkgLine +
            "import lombok.Getter;\n" +
            "import lombok.RequiredArgsConstructor;\n" +
            "import java.util.*;\n" +
            "\n" +
            "@Getter\n" +
            "@RequiredArgsConstructor\n" +
            "public enum " + enumName + " {\n\n" +
            constantsJoiner + ";\n\n" +
            "    private final int code;\n\n" +
            "    private final String description;\n\n" +
            "    private static final Map<Integer, " + enumName + "> ENUM_MAP;\n" +
            "    static {\n" +
            "        Map<Integer, " + enumName + "> m = new HashMap<>();\n" +
            "        for (" + enumName + " e : values()) {\n" +
            "            m.put(e.code, e);\n" +
            "        }\n" +
            "        ENUM_MAP = Collections.unmodifiableMap(m);\n" +
            "    }\n\n" +
            "    public static Optional<" + enumName + "> find(int code) {\n" +
            "        return Optional.ofNullable(ENUM_MAP.get(code));\n" +
            "    }\n\n" +
            "    public static " + enumName + " require(int code) {\n" +
            "        " + enumName + " e = ENUM_MAP.get(code);\n" +
            "        if (e == null) throw new IllegalArgumentException(\"Unknown code: \" + code);\n" +
            "        return e;\n" +
            "    }\n" +
            "}\n";
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void writeEnumFile(Project project, PsiDirectory dir, String enumName, String source) {
        WriteCommandAction.runWriteCommandAction(project, () -> {
            String fileName = enumName + ".java";
            if (dir.findFile(fileName) != null) {
                notify(project, "文件已存在: " + fileName, NotificationType.WARNING);
                return;
            }
            PsiFileFactory factory = PsiFileFactory.getInstance(project);
            PsiFile file = factory.createFileFromText(fileName, JavaFileType.INSTANCE, source);

            PsiElement added = dir.add(file);

            // 格式化 + 优化 import
            CodeStyleManager.getInstance(project).reformat(added);
            JavaCodeStyleManager.getInstance(project).optimizeImports((PsiFile) added);

            notify(project, "已生成枚举: " + fileName, NotificationType.INFORMATION);
        });
    }

    private void notify(Project project, String message, NotificationType type) {
        if (project == null) return;
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_ID)
            .createNotification(message, type)
            .notify(project);
    }
    
    

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    private static class EnumEntry {
        final String name;
        final int code;
        final String description;
        EnumEntry(String name, int code, String description) {
            this.name = name;
            this.code = code;
            this.description = description;
        }
    }
}