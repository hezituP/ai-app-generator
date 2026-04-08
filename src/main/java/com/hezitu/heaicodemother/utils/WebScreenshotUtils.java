package com.hezitu.heaicodemother.utils;

import cn.hutool.core.io.FileUtil;
import com.hezitu.heaicodemother.exception.BusinessException;
import com.hezitu.heaicodemother.exception.ErrorCode;
import io.github.bonigarcia.wdm.WebDriverManager;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.springframework.stereotype.Component;

import java.io.File;
import java.time.Duration;

@Component
@Slf4j
public class WebScreenshotUtils {

    private static final int DEFAULT_WIDTH = 1600;

    private static final int DEFAULT_HEIGHT = 900;

    private static final WebDriver webDriver;

    static {
        webDriver = initChromeDriver(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    @PreDestroy
    public void destroy() {
        if (webDriver != null) {
            webDriver.quit();
        }
    }

    /**
     * 初始化 Chrome 浏览器驱动
     */
    private static WebDriver initChromeDriver(int width, int height) {
        try {
            WebDriverManager.chromedriver().setup();
            ChromeOptions options = new ChromeOptions();
            options.addArguments("--headless");
            options.addArguments("--disable-gpu");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments(String.format("--window-size=%d,%d", width, height));
            options.addArguments("--disable-extensions");
            options.addArguments("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            WebDriver driver = new ChromeDriver(options);
            driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));
            driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10));
            return driver;
        } catch (Exception e) {
            log.error("初始化 Chrome 浏览器失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "初始化 Chrome 浏览器失败");
        }
    }

    /**
     * 对指定 URL 进行截图，并写入目标文件
     */
    public synchronized void takeScreenshot(String url, String outputPath) {
        try {
            log.info("开始网站截图, url: {}, outputPath: {}", url, outputPath);
            webDriver.get(url);
            File screenshotFile = ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.FILE);
            FileUtil.mkParentDirs(outputPath);
            FileUtil.copy(screenshotFile, new File(outputPath), true);
            log.info("网站截图成功, outputPath: {}", outputPath);
        } catch (Exception e) {
            log.error("网站截图失败, url: {}", url, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "网站截图失败");
        }
    }

    /**
     * 对指定 URL 进行截图，返回图片字节数组
     */
    public synchronized byte[] takeScreenshot(String url) {
        try {
            log.info("开始网站截图, url: {}", url);
            webDriver.get(url);
            return ((TakesScreenshot) webDriver).getScreenshotAs(OutputType.BYTES);
        } catch (Exception e) {
            log.error("网站截图失败, url: {}", url, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "网站截图失败");
        }
    }
}
