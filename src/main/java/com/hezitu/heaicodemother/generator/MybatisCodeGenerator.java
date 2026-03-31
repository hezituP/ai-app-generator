package com.hezitu.heaicodemother.generator;

import cn.hutool.core.lang.Dict;
import cn.hutool.setting.yaml.YamlUtil;
import com.mybatisflex.codegen.Generator;
import com.mybatisflex.codegen.config.GlobalConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.util.Map;

/**
 * Mybatis 代码生成器
 *
 * @author hezitu
 */
public class MybatisCodeGenerator {

    // 要生成的表名，按需修改
    private static final String[] TABLE_NAMES = {"chat_history"};

    public static void main(String[] args) {
        // 获取配置信息
        Dict dict = YamlUtil.loadByPath("application.yml");
        Map<String, Object> dataSourceConfig = dict.getByPath("spring.datasource");
        String url = String.valueOf(dataSourceConfig.get("url"));
        String username = String.valueOf(dataSourceConfig.get("username"));
        String password = String.valueOf(dataSourceConfig.get("password"));

        // 配置数据源
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);

        // 创建配置
        GlobalConfig globalConfig = createGlobalConfig();

        // 通过 datasource 和 globalConfig 创建代码生成器
        Generator generator = new Generator(dataSource, globalConfig);

        // 生成代码
        generator.generate();
    }

    public static GlobalConfig createGlobalConfig() {
        GlobalConfig globalConfig = new GlobalConfig();

        // ✅ 设置代码输出目录（src/main/java 路径）
        globalConfig.getPackageConfig()
                .setBasePackage("com.hezitu.heaicodemother")
                .setSourceDir(System.getProperty("user.dir") + "/he-ai-code-mother/src/main/java");

        // ✅ 只生成指定的表
        globalConfig.getStrategyConfig()
                .setGenerateTable(TABLE_NAMES)
                // 设置逻辑删除字段
                .setLogicDeleteColumn("isDelete");

        // 设置生成 entity 并启用 Lombok
        globalConfig.enableEntity()
                .setWithLombok(true)
                .setJdkVersion(21);

        // 设置生成 mapper
        globalConfig.enableMapper();
        globalConfig.enableMapperXml();

        // 设置生成 SERVICE
        globalConfig.enableService();
        globalConfig.enableServiceImpl();

        // 设置生成 CONTROLLER
        globalConfig.enableController();

        // 设置生成注释
        globalConfig.getJavadocConfig()
                .setAuthor("hezitu")
                .setSince("");

        return globalConfig;
    }
}
