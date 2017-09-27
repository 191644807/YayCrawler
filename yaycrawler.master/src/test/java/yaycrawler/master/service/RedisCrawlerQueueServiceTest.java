package yaycrawler.master.service;

import org.apache.commons.collections.map.HashedMap;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import yaycrawler.common.model.CrawlerRequest;
import yaycrawler.master.Application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by yuananyun on 2016/8/7.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@SpringBootTest(classes = Application.class)
@WebAppConfiguration
public class RedisCrawlerQueueServiceTest {

    @Autowired
    private CrawlerQueueServiceFactory crawlerQueueServiceFactory;

    @Value("${crawler.queue.dataType:redis}")
    private String dataType;

    @Test
    public  void testAddCrawlerReqeust()
    {
        ICrawlerQueueService crawlerQueueService = crawlerQueueServiceFactory.getCrawlerQueueServiceByDataType(dataType);
        List<CrawlerRequest> requestsList=new ArrayList<>();
        for(int i=0;i<300;i++) {
            CrawlerRequest request = new CrawlerRequest("www.baidu.com/id="+System.currentTimeMillis() , "baidu.com", "post");
            Map data = new HashedMap();
            data.put("name", "liushuishang");
            request.setData(data);
            requestsList.add(request);
            crawlerQueueService.pushTasksToWaitingQueue(requestsList, false);
            requestsList.clear();
        }
    }

    @Test
    public void fetchTasksFromWaitingQueueTest()
    {
        ICrawlerQueueService crawlerQueueService = crawlerQueueServiceFactory.getCrawlerQueueServiceByDataType(dataType);
        Assert.assertEquals(crawlerQueueService.fetchTasksFromWaitingQueue(100).size(), 100);
    }

    @Test
    public void moveWaitingTaskToRunningQueueTest()
    {
        ICrawlerQueueService crawlerQueueService = crawlerQueueServiceFactory.getCrawlerQueueServiceByDataType(dataType);
        Assert.assertTrue(crawlerQueueService.moveWaitingTaskToRunningQueue("", crawlerQueueService.fetchTasksFromWaitingQueue(100)));
    }

    @Test
    public void moveRunningTaskToFailQueueTest()
    {
        ICrawlerQueueService crawlerQueueService = crawlerQueueServiceFactory.getCrawlerQueueServiceByDataType(dataType);
        Assert.assertTrue(crawlerQueueService.moveRunningTaskToFailQueue("769b077660d7b995f9a84de53da67554c0c70238","执行失败！"));
    }
    @Test
    public void moveRunningTaskToSuccessQueueTest()
    {
//        Assert.assertTrue(crawlerQueueService.moveRunningTaskToSuccessQueue("3090bb51dbca665ad571a44b9e992e76bba3b0a7"));
    }
    @Test
    public void refreshBreakedQueueTest()
    {
        ICrawlerQueueService crawlerQueueService = crawlerQueueServiceFactory.getCrawlerQueueServiceByDataType(dataType);
        crawlerQueueService.refreshBreakedQueue(60000L);
    }

}
