package com.github.hyacinth.generator;

import com.github.hyacinth.sql.RawSqls;
import com.github.hyacinth.sql.SqlCache;
import com.github.hyacinth.tools.StringTools;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * 生成Sql映射文件
 * <p>
 * Author: luoyong
 * Email: lcrysman@gmail.com
 * Date: 2017/3/24
 * Time: 10:17
 */
public class SqlsGenerator {

    protected String packageTemplate =
            "package %s;%n%n";
    protected String importTemplate =
            "import com.github.hyacinth.SqlKey;%n%n";
    protected String classDefineTemplate =
            "/**%n" +
                    " * Generated by hyacinth.%n" +
                    " */%n" +
                    "public enum %s implements SqlKey {%n%n";
    protected String enumKeyTemplate =
            "\t%s,%n%n";

    protected String enumKeyCommentTemplate =
            "\t * %s";

    protected String sqlsClassName = "Sqls";
    protected String sqlsPackageName;
    protected String sqlsOutputDir;

    public SqlsGenerator(String sqlsPackageName, String sqlsOutputDir, String sqlsClassName) {
        if (StringTools.isBlank(sqlsPackageName)) {
            throw new IllegalArgumentException("sqlsPackageName can not be blank.");
        }
        if (StringTools.isBlank(sqlsOutputDir)) {
            throw new IllegalArgumentException("sqlsOutputDir can not be blank.");
        }
        if (StringTools.isBlank(sqlsClassName)) {
            throw new IllegalArgumentException("sqlsClassName can not be blank.");
        }
        this.sqlsClassName = sqlsClassName;
        this.sqlsPackageName = sqlsPackageName;
        this.sqlsOutputDir = sqlsOutputDir;
    }

    public SqlsGenerator(String sqlsPackageName, String sqlsOutputDir) {
        if (StringTools.isBlank(sqlsPackageName)) {
            throw new IllegalArgumentException("sqlsPackageName can not be blank.");
        }
        if (StringTools.isBlank(sqlsOutputDir)) {
            throw new IllegalArgumentException("sqlsOutputDir can not be blank.");
        }
        this.sqlsPackageName = sqlsPackageName;
        this.sqlsOutputDir = sqlsOutputDir;
    }

    protected void genPackage(StringBuilder ret) {
        ret.append(String.format(packageTemplate, sqlsPackageName));
    }

    protected void genImport(StringBuilder ret) {
        ret.append(String.format(importTemplate));
    }

    protected void genClassDefine(StringBuilder ret) {
        ret.append(String.format(classDefineTemplate, sqlsClassName));
    }

    protected void genEnumKey(StringBuilder ret, String key) {
        ret.append(String.format(enumKeyTemplate, key));
    }

    protected void genEnumKeyComment(StringBuilder ret, String comment) {
        ret.append(String.format(enumKeyCommentTemplate, comment));
    }

    protected void generate() {
        System.out.println("Generate sqls ...");
        System.out.println("sqls Output Dir: " + sqlsOutputDir);
        StringBuilder ret = new StringBuilder();
        genPackage(ret);
        genImport(ret);
        genClassDefine(ret);

        for (String key : SqlCache.rawSqls.asMap().keySet()) {
            RawSqls rawSqls = SqlCache.rawSqls.get(key);
            String comment = rawSqls.getComment();
            if (comment != null) {
                String[] comments = comment.split("\\n");
                ret.append("\t/**\n");
                for (String c : comments) {
                    genEnumKeyComment(ret, c);
                }
                ret.append("\t */\n");
            }
            genEnumKey(ret, key.replace("*", ""));
        }
        ret.substring(0, ret.lastIndexOf(","));
        ret.append(String.format("}%n"));

        writeToFile(ret.toString());
    }


    private void writeToFile(String content) {
        File dir = new File(sqlsOutputDir);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        String target = new StringBuilder().append(sqlsOutputDir).append(File.separator).append(sqlsClassName).append(".java").toString();
        FileWriter fw = null;
        try {
            fw = new FileWriter(target);
            fw.write(content);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                fw.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
