package com.versioneye.mojo;


import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import versioneye.domain.GlobalSetting;
import versioneye.domain.MavenRepository;
import versioneye.domain.Repository;
import versioneye.persistence.IGlobalSettingDao;
import versioneye.persistence.IMavenRepostoryDao;
import versioneye.persistence.IProductDao;
import versioneye.service.ProductService;

/*
    This Mojo is used in VersionEye Enterprise to crawl internal repositories.
 */
@Mojo( name = "repo1index", defaultPhase = LifecyclePhase.PROCESS_SOURCES )
public class Repo1IndexMojo extends CentralMojo {

    private String username;
    private String password;

    public void execute() throws MojoExecutionException, MojoFailureException {
        try{
            ApplicationContext context = new ClassPathXmlApplicationContext("applicationContext.xml");

            productDao         = (IProductDao)        context.getBean("productDao");
            mavenRepositoryDao = (IMavenRepostoryDao) context.getBean("mavenRepositoryDao");
            globalSettingDao   = (IGlobalSettingDao)  context.getBean("globalSettingDao");

            productService     = (ProductService) context.getBean("productService");

            fetchUserAndPassword();
            String baseUrl = fetchBaseUrl();

            if (!baseUrl.contains(";")) {
                crawlUrl(baseUrl, 0);
            } else {
                int z = 0;
                String[] splits = baseUrl.split(";");
                for (String url : splits){
                    crawlUrl(url, z);
                    z += 1;
                }
            }
        } catch( Exception exception ){
            getLog().error(exception);
            throw new MojoExecutionException("Oh no! Something went wrong. Get in touch with the VersionEye guys and give them feedback.", exception);
        }
    }

    private void crawlUrl(String baseUrl, int count){
        try{
            String name = "MavenInternal" + count;
            getLog().info("-- Starting to crawl " + name + " with base Url: " + baseUrl);

            mavenRepository = new MavenRepository();
            mavenRepository.setName(name);
            mavenRepository.setUrl(baseUrl);
            mavenRepository.setUsername(username);
            mavenRepository.setPassword(password);
            mavenRepository.setLanguage("Java");

            addRepo(mavenRepository);

            super.doUpdateFromIndex();
        } catch (Exception ex){
            getLog().error(ex);
        }
    }

    private String fetchBaseUrl(){
        String env = System.getenv("RAILS_ENV");
        getLog().info("fetchBaseUrl for env: " + env );
        try{
            GlobalSetting gs = globalSettingDao.getBy(env, "mvn_repo_1");
            String url = gs.getValue();
            getLog().info(" - mvn_repo_1: " + url);
            return url;
        } catch( Exception ex){
            ex.printStackTrace();
            return "http://repo.maven.apache.org/maven2";
        }
    }

    private void fetchUserAndPassword(){
        String env = System.getenv("RAILS_ENV");
        try{
            username = globalSettingDao.getBy(env, "mvn_repo_1_user").getValue();
            password = globalSettingDao.getBy(env, "mvn_repo_1_password").getValue();
        } catch( Exception ex){
            ex.printStackTrace();
            username = null;
            password = null;
        }
    }

}
