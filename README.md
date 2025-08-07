# Javadoc to Enum Generator

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)

这是一个为 IntelliJ IDEA 设计的插件，旨在通过从类属性的 Javadoc 注释中自动生成对应的 Java 枚举类，来提升您的开发效率。

## ✨ 功能特性

* **一键生成**: 在类属性上右键点击，即可快速生成枚举类。
* **智能命名**:
    * 自动根据 `源类名 + 属性名` 的规则生成枚举名（例如 `CampaignDO` 的 `status` 属性会生成 `CampaignStatusEnum`）。
    * 自动移除源类名中常见的后缀（如 `DO`, `DTO`, `VO`）。
* **灵活的 Javadoc 解析**: 支持多种常见的键值对格式。
* **功能完备**: 生成的枚举类不仅包含 `code` 和 `description` 属性，还自动提供了：
    * 一个 `Map` 集合用于快速查找。
    * 一个 `parse(int type)` 方法。
    * 一个返回 `Optional` 的 `parseOptional(int type)` 安全解析方法。

## 🚀 如何使用

#### 1. 准备您的 Java 类

首先，在您的类中定义一个属性，并为其编写特定格式的 Javadoc 注释。

**示例 `CampaignDO.java`:**
```java
public class CampaignDO {

    /**
     * 0-待处理,1-处理中,2-已完成,3-失败
     */
    private int status;

}
```

#### 2. 右键点击并生成

将鼠标光标**精确地放在属性名上**（例如 `status`），然后右键点击，在弹出的菜单中选择 **"Generate Enum"**。

![操作演示](https://i.imgur.com/example.gif "操作演示")

#### 3. 查看生成结果

插件会自动在与源文件相同的目录下创建一个新的枚举文件。

**生成的 `CampaignStatusEnum.java`:**
```java
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
@AllArgsConstructor
public enum CampaignStatusEnum {

    A(0, "待处理"),

    B(1, "处理中"),

    C(2, "已完成"),
    
    D(3, "失败");

    private final int code;
    private final String description;

    public static final Map<Integer, CampaignStatusEnum> ENUM_MAP = Arrays.stream(CampaignStatusEnum.values())
            .collect(Collectors.toMap(CampaignStatusEnum::getCode, e -> e));

    public static CampaignStatusEnum parse(int type) {
        return ENUM_MAP.get(type);
    }

    public static Optional<CampaignStatusEnum> parseOptional(int type) {
        return Optional.ofNullable(ENUM_MAP.get(type));
    }
}
```

## ⚙️ 支持的 Javadoc 格式

为了提供最大的灵活性，本插件支持解析以下多种格式的键值对，您可以在项目中混合使用它们：

```java
/**
 * 支持格式如下:
 * 0-待处理
 * 1:处理中
 * 2：已完成   (中文冒号)
 * 3 = 失败    (等号)
 * 4 - 已归档  (带空格)
 *
 * 同样支持逗号或换行作为多个键值对的分隔符。
 * 5:已取消, 6-已删除
 */
```

## 📦 安装

#### 方式一：从 JetBrains Marketplace 安装 (推荐)

1.  打开 **Settings/Preferences** -> **Plugins**。
2.  切换到 **Marketplace** 标签页。
3.  搜索 "Javadoc to Enum Generator"。
4.  点击 **Install** 并根据提示重启 IDE。

#### 方式二：从本地磁盘安装

1.  从 [GitHub Releases]() 页面下载最新的 `.zip` 文件。
2.  打开 **Settings/Preferences** -> **Plugins**。
3.  点击齿轮图标 (⚙️) -> **Install Plugin from Disk...**。
4.  选择你刚刚下载的 `.zip` 文件并安装，然后重启 IDE。

## 📄 许可证

本项目基于 [MIT License](LICENSE) 开源。

---
**