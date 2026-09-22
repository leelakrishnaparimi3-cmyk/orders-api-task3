package com.orders;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;
import java.sql.Connection;
@SpringBootApplication
@RestController
public class ordersApplication {
 private final DataSource dataSource;
 public ordersApplication(DataSource dataSource){this.dataSource=dataSource;}
 public static void main(String[] args){SpringApplication.run(ordersApplication.class,args);}
 @GetMapping("/") public String home(){return "Orders API - Version: "+version()+" - Environment: "+env();}
 @GetMapping("/health") public String health(){return "UP";}
 @GetMapping("/version") public String versionEndpoint(){return version();}
 @GetMapping("/db-check") public String dbCheck(){try(Connection c=dataSource.getConnection()){return "DATABASE CONNECTED: "+c.getMetaData().getDatabaseProductName();}catch(Exception e){return "DATABASE CONNECTION FAILED";}}
 private String version(){return System.getenv().getOrDefault("APP_VERSION","UNKNOWN");}
 private String env(){return System.getenv().getOrDefault("APP_ENV","PRODUCTION");}
}
