package com.versioneye.mojo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.versioneye.persistence.IMavenRepostoryDao;
import com.versioneye.persistence.IProductDao;
import com.versioneye.service.ProductService;

/**
 * Crawles clojar M2 Repository for Clojure
 */
@Mojo( name = "clojars", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class ClojarsMojo extends CentralMojo {

    static final Logger logger = LogManager.getLogger(ClojarsMojo.class.getName());

    public void execute() throws MojoExecutionException, MojoFailureException {
        try{
            ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");
            productService = (ProductService) context.getBean("productService");
            mavenRepositoryDao = (IMavenRepostoryDao) context.getBean("mavenRepositoryDao");
            productDao = (IProductDao) context.getBean("productDao");

            mavenRepository = mavenRepositoryDao.findByName("cloJars");
            addRepo(mavenRepository);

            super.doUpdateFromIndex();
        } catch( Exception exception ){
            logger.error(exception);
            throw new MojoExecutionException("Oh no! Something went wrong. Get in touch with the VersionEye guys and give them feedback.", exception);
        }
    }

}
