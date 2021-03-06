package com.github.hyacinth.support.spring;

import com.github.hyacinth.*;
import com.github.hyacinth.sql.DefaultCompiler;
import com.github.hyacinth.tools.StringTools;
import com.github.hyacinth.dialect.Dialect;
import com.github.hyacinth.sql.markdown.MdFileMonitor;
import com.github.hyacinth.sql.DefaultBuilder;
import com.github.hyacinth.sql.markdown.MdResolve;
import com.github.hyacinth.tools.PathTools;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;

import javax.sql.DataSource;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Spring Bean
 * 支持Spring 容器启动
 * <p>
 * Author: luoyong
 * Email: lcrysman@gmail.com
 * Date: 2017/2/28
 * Time: 15:04
 */
public class HyacinthConfiguration {

    DataSource dataSource;
    //热加载
    Boolean isHotLoad;
    //sql 模板文件路径
    Resource[] mdLocations;
    //配置
    String configName;

    String basePackage;

    Dialect dialect;

    long interval = 5000;

    boolean showSql = false;

    boolean isStarted = false;

    MdFileMonitor monitor;

    public void setDataSource(DataSource dataSource) {
        if (!(dataSource instanceof TransactionAwareDataSourceProxy)) {
            //TransactionAwareDataSourceProxy是事务的最底层，它代理了DataSource，并增加了spring管理事务功能。
            this.dataSource = new TransactionAwareDataSourceProxy(dataSource);
        } else {
            this.dataSource = dataSource;
        }
    }

    public void setDialect(String dialect) throws ClassNotFoundException, IllegalAccessException, InstantiationException {
        this.dialect = (Dialect) Class.forName(dialect).newInstance();
    }

    public void setIsHotLoad(Boolean hotLoad) {
        this.isHotLoad = hotLoad;
    }

    public void setMdLocations(Resource[] mdLocations) {
        this.mdLocations = mdLocations;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    public void setShowSql(boolean showSql) {
        this.showSql = showSql;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public void setInterval(long interval) {
        this.interval = interval;
    }

    public void init() throws IOException {
        if (isStarted) return;
        if (StringTools.isBlank(configName)) {
            throw new IllegalArgumentException("configName can not be blank");
        }
        if (dataSource == null) {
            throw new IllegalArgumentException("dataSource can not be null");
        }
        Config config = new Config(configName, dataSource, showSql, dialect);

        if (config.getDataSource() == null && dataSource != null) {
            config.setDataSource(dataSource);
        }

        MdResolve.setTemplateCompiler(new DefaultCompiler());

        //处理sql资源文件
        processSqlFile(isHotLoad);

        new TableBuilder().build(getBaseModelClass(basePackage), config);
        DbKit.addConfig(config);
        DbKit.setSqlBuilder(new DefaultBuilder());
        isStarted = true;

    }

    /**
     * 开启sql文件热加载
     */
    private void processSqlFile(boolean isHotLoad) throws IOException {
        List<File> files = new ArrayList<File>();
        List<File> folders = new ArrayList<File>();
        List<String> foldersPaths = new ArrayList<String>();
        for (Resource resource : mdLocations) {
            File file = resource.getFile();
            files.add(file);
            File folder = file.getParentFile();
            String path = folder.getAbsolutePath();
            if (!foldersPaths.contains(path)) {
                foldersPaths.add(path);
                folders.add(folder);
            }
        }
        //解析sql文件
        new MdResolve().resolve(files);
        //如果配置上，开启了热加载功能，则启用文件监听进程
        if (isHotLoad) {
            if (this.monitor == null) {
                this.monitor = new MdFileMonitor();
            }

            monitor.addListener(folders, interval);
        }
    }

    /**
     * 获取modelclass
     *
     * @param basePackage
     * @return
     * @throws IOException
     */
    private List<Class<? extends Model<?>>> getBaseModelClass(String basePackage) throws IOException {
        List<Class<? extends Model<?>>> classes = new ArrayList<Class<? extends Model<?>>>();

        String classPattern = "classpath*:" + basePackage.replace(".", "/") + "/*.class";
        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources(classPattern);

        //获取类路径
        for (Resource resource : resources) {
            URL url = resource.getURL();
            String protocol = url.getProtocol(),
                    className = null;
            if (protocol.equals("file")) {
                //获取class文件所在磁盘路径
                String classFilePath = resource.getFile().getAbsolutePath();
                className = classFilePath.substring(classFilePath.lastIndexOf("classes" + File.separator) + 8, classFilePath.length() - 6).replace(File.separator, ".");
            } else if (protocol.equals("jar")) {
                String classUrlPath = url.getPath();
                //从jar url中截图类名
                className = classUrlPath.substring(classUrlPath.indexOf(".jar!/") + 6, classUrlPath.length() - 6).replace("/", ".");
            }
            if (className != null && !className.contains("$")) {
                try {
                    Class<?> clazz = Class.forName(className);
                    if (Model.class.isAssignableFrom(clazz)) {
                        classes.add((Class<? extends Model<?>>) clazz);
                    }
                } catch (ClassNotFoundException e) {
                    e.printStackTrace();
                }
            }
        }
        return classes;
    }


    /**
     * spring 容器销毁bean时调用此方法
     * 方法将停止文件监控进程
     *
     * @throws InterruptedException
     */
    public void close() throws InterruptedException {
        //停止文件监控进程
        if (this.monitor != null) {
            this.monitor.shutdown();
        }
    }
}
