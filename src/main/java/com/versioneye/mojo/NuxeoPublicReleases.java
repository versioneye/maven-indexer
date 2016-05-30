package com.versioneye.mojo;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import com.versioneye.domain.MavenRepository;
import com.versioneye.persistence.IMavenRepostoryDao;
import com.versioneye.persistence.IProductDao;
import com.versioneye.service.ProductService;


@Mojo( name = "nuxeo-public-releases", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class NuxeoPublicReleases extends CentralMojo {

    static final Logger logger = LogManager.getLogger(NuxeoPublicReleases.class.getName());

    public void execute() throws MojoExecutionException, MojoFailureException {
        try{
            ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");

            productService     = (ProductService) context.getBean("productService");
            mavenRepositoryDao = (IMavenRepostoryDao) context.getBean("mavenRepositoryDao");
            productDao         = (IProductDao) context.getBean("productDao");

            MavenRepository publicNuxeoRepo = mavenRepositoryDao.findByName("nuxeo");
            mavenRepository                 = mavenRepositoryDao.findByName("nuxeo-public-releases");

            addRepo(mavenRepository);
            addRepo(publicNuxeoRepo);

            super.doUpdateFromIndex();
        } catch( Exception exception ){
            logger.error(exception);
            throw new MojoExecutionException("Oh no! Something went wrong. Get in touch with the VersionEye guys and give them feedback.", exception);
        }
    }

}
