package io.github.weihubeats.enumgenerate;

import com.intellij.ide.highlighter.JavaFileType;
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
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jetbrains.annotations.NotNull;

public class GenerateEnumAction extends AnAction {

    @Override
    public void update(@NotNull AnActionEvent e) {
        // 控制 Action 的可见性
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);
        // 只有当选中的元素是 PsiField (类属性)时，才显示此 Action
        e.getPresentation().setEnabledAndVisible(psiElement instanceof PsiField);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        // 获取当前上下文信息
        Project project = e.getProject();
        PsiElement psiElement = e.getData(CommonDataKeys.PSI_ELEMENT);

        if (project == null || !(psiElement instanceof PsiField)) {
            return;
        }

        PsiField selectedField = (PsiField) psiElement;
        PsiClass containingClass = selectedField.getContainingClass();
        if (containingClass == null) {
            return;
        }

        // 1. 解析 Javadoc
        String javadocComment = Objects.requireNonNull(selectedField.getDocComment()).getText();
        String enumConstantsString = parseJavadoc(javadocComment);
        if (enumConstantsString.isEmpty()) {
            // 可以添加一个提示，告知用户 Javadoc 格式不正确
            return;
        }

        // 2. 生成枚举类名
        String baseClassName = containingClass.getName().replaceAll("(DO|DTO|VO)$", "");
        String fieldName = selectedField.getName();
        String capitalizedFieldName = fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
        String enumName = baseClassName + capitalizedFieldName + "Enum";

        // 3. 生成枚举类代码
        String enumCode = generateEnumCode(enumName, enumConstantsString);

        // 4. 在同一个目录下创建文件并写入代码
        PsiDirectory containingDirectory = containingClass.getContainingFile().getContainingDirectory();
        createEnumFile(project, containingDirectory, enumName, enumCode);
    }

    /**
     * 解析 Javadoc 注释，提取枚举常量信息
     * 格式一: 0-待处理
     * 格式二: 1:处理中
     * 格式三: 2：已完成
     * 格式四: 3 = 失败
     * 格式五(带空格): 4 - 已归档
     * 也可以混用，用逗号或换行分隔
     * 5:已取消, 6-已删除
     * @param javadoc Javadoc 字符串
     * @return 格式化后的枚举常量字符串
     */
    private String parseJavadoc(String javadoc) {
        // 新的正则表达式，更强大、更灵活
        // \s* 表示匹配零个或多个空格
        // [-:：=] 表示匹配 - 或 : 或 ：(中文冒号) 或 =
        Pattern pattern = Pattern.compile("(\\d+)\\s*[-:：=]\\s*([^,\\n]*)");
        Matcher matcher = pattern.matcher(javadoc);
        StringBuilder constantsBuilder = new StringBuilder();
        int count = 0;
        char enumConstName = 'A';

        while (matcher.find()) {
            if (count > 0) {
                constantsBuilder.append(",\n\n");
            }
            String code = matcher.group(1).trim();
            String description = matcher.group(2).trim();
            // 使用 A, B, C... 作为枚举名
            constantsBuilder.append("    ").append(enumConstName++).append("(").append(code).append(", \"").append(description).append("\")");
            count++;
        }
        if(count > 0) {
            constantsBuilder.append(";");
        }

        return constantsBuilder.toString();
    }

    /**
     * 生成完整的枚举类代码
     *
     * @param enumName          枚举类名
     * @param enumConstants     枚举常量字符串
     * @return 完整的 Java 代码
     */
    private String generateEnumCode(String enumName, String enumConstants) {
        return "import lombok.RequiredArgsConstructor;\n" +
            "import lombok.Getter;\n\n" +
            "import java.util.Arrays;\n" +
            "import java.util.Map;\n" +
            "import java.util.Optional;\n" + 
            "import java.util.stream.Collectors;\n\n" +
            "@Getter\n" +
            "@RequiredArgsConstructor\n" +
            "public enum " + enumName + " {\n\n" +
            enumConstants + "\n\n" +
            "    private final int code;\n" +
            "    private final String description;\n\n" +
            "    public static final Map<Integer, " + enumName + "> ENUM_MAP = Arrays.stream(" + enumName + ".values())\n" +
            "            .collect(Collectors.toMap(" + enumName + "::getCode, e -> e));\n\n" +
            "    public static " + enumName + " parse(int type) {\n" +
            "        return ENUM_MAP.get(type);\n" +
            "    }\n\n" +

            "    public static Optional<" + enumName + "> parseOptional(int type) {\n" +
            "        return Optional.ofNullable(ENUM_MAP.get(type));\n" +
            "    }\n" +

            "}\n";
    }


    /**
     * 创建并写入枚举文件
     *
     * @param project     当前项目
     * @param directory   目标目录
     * @param enumName    枚举类名
     * @param enumCode    枚举代码
     */
    private void createEnumFile(Project project, PsiDirectory directory, String enumName, String enumCode) {
        // 使用 WriteCommandAction 来执行文件写入操作，确保操作是可撤销的
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                String fileName = enumName + ".java";
                // 检查文件是否已存在
                if (directory.findFile(fileName) != null) {
                    // 文件已存在，可以提示用户或直接覆盖
                    System.out.println("File " + fileName + " already exists.");
                    return;
                }
                PsiFileFactory fileFactory = PsiFileFactory.getInstance(project);
                PsiFile javaFile = fileFactory.createFileFromText(fileName, JavaFileType.INSTANCE, enumCode);
                directory.add(javaFile);
                System.out.println("Successfully generated " + fileName);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        });
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}