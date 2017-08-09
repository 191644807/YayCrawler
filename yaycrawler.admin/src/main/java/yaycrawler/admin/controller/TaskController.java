package yaycrawler.admin.controller;

import com.alibaba.fastjson.JSON;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import yaycrawler.admin.communication.MasterActor;
import yaycrawler.admin.quartz.CrawlerRequestJob;
import yaycrawler.admin.service.CrawlerResultRetrivalService;
import yaycrawler.admin.service.TaskService;
import yaycrawler.common.model.CrawlerRequest;
import yaycrawler.common.model.QueueQueryParam;
import yaycrawler.common.utils.UrlUtils;
import yaycrawler.quartz.model.ScheduleJobInfo;
import yaycrawler.quartz.service.QuartzScheduleService;
import yaycrawler.quartz.utils.CronExpressionUtils;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by ucs_yuananyun on 2016/5/17.
 */
@Controller
public class TaskController {

    @Autowired
    private MasterActor masterActor;

    @Autowired
    private CrawlerResultRetrivalService resultRetrivalService;

    @Autowired
    private QuartzScheduleService quartzScheduleService;

    @Autowired
    private TaskService taskService;

    @RequestMapping(value = "/publishTask", method = RequestMethod.GET)
    public ModelAndView getPublishTask() {
        return new ModelAndView("task_publish");
    }

    @RequestMapping(value = "/publishTask", method = RequestMethod.POST)
    @ResponseBody
    public Object postPublishTask(@RequestBody Map taskMap) {
        String url = MapUtils.getString(taskMap, "pageUrl");
        String method = MapUtils.getString(taskMap, "method");
        Assert.notNull(url,"抓取链接不能为空！");
        Assert.notNull(method,"抓取方法不能为空！");

        List<Map> paramMapList = (List<Map>) taskMap.get("paramList");
        CrawlerRequest[] crawlerRequestArray = new CrawlerRequest[paramMapList==null?1:paramMapList.size()];
        if (paramMapList!=null&&paramMapList.size() > 0) {
            String paramJson = MapUtils.getString(taskMap, "urlParamsJson");
            Map<String, Object> data = new HashMap<>();
            if (!StringUtils.isBlank(paramJson)) {
                data = JSON.parseObject(paramJson, Map.class);
            }
            for (int i = 0; i < paramMapList.size(); i++) {
                Map paramMap = paramMapList.get(i);
                if (paramMap == null) continue;
                CrawlerRequest crawlerRequest = new CrawlerRequest(url, UrlUtils.getDomain(url), method.toUpperCase());
                for (Map.Entry<String, Object> entry : data.entrySet()) {
                    paramMap.put(entry.getKey(),entry.getValue());
                }
                crawlerRequest.setData(paramMap);
                crawlerRequestArray[i] = crawlerRequest;
            }
        }else{
            String paramJson = MapUtils.getString(taskMap, "urlParamsJson");
            Map<String, Object> data = new HashMap<>();
            if (!StringUtils.isBlank(paramJson)) {
                data = JSON.parseObject(paramJson, Map.class);
            }
            CrawlerRequest crawlerRequest = new CrawlerRequest(url, UrlUtils.getDomain(url), method.toUpperCase());
            crawlerRequest.setData(data);
            crawlerRequestArray[0] = crawlerRequest;
        }

        ScheduleJobInfo jobInfo = null;
        String jobType = MapUtils.getString(taskMap, "jobType");
        if ("regular".equals(jobType)) {
            String jobName = MapUtils.getString(taskMap, "jobName");
            String jobGroup = MapUtils.getString(taskMap, "jobGroup");
            String cronExpression = MapUtils.getString(taskMap, "cronExpression");
            String description = MapUtils.getString(taskMap, "description");

            Assert.notNull(jobName,"工作名称不能为空！");
            Assert.notNull(jobGroup,"工作组不能为空！");
            Assert.notNull(cronExpression,"定时时间不能为空！");

            cronExpression = CronExpressionUtils.convertToSpringCron(cronExpression);
            if (!CronExpressionUtils.isValidExpression(cronExpression))
                throw new RuntimeException("Cron表达式不正确！");

            jobInfo = new ScheduleJobInfo(jobName, jobGroup, cronExpression);
            jobInfo.setDescription(description);
        }

        boolean flag = masterActor.publishTasks(crawlerRequestArray);
        if (flag && jobInfo != null) {
            CrawlerRequestJob job = new CrawlerRequestJob(jobInfo);
            job.addCrawlerRequest(crawlerRequestArray);
            return quartzScheduleService.addJob(job);
        }
        return flag;
    }

    @RequestMapping("/successQueueManagement")
    public ModelAndView successQueueManagement() {
        ModelAndView modelAndView = new ModelAndView("successqueue_management");
        modelAndView.addObject("queue", "success");
        return modelAndView;
    }

    @RequestMapping("/failQueueManagement")
    public ModelAndView failQueueManagement() {
        ModelAndView modelAndView = new ModelAndView("failqueue_management");
        modelAndView.addObject("queue", "fail");
        return modelAndView;
    }

    @RequestMapping("/itemQueueManagement")
    public ModelAndView itemQueueManagement() {
        ModelAndView modelAndView = new ModelAndView("itemqueue_management");
        modelAndView.addObject("queue", "waiting");
        return modelAndView;
    }

    @RequestMapping("/runningQueueManagement")
    public ModelAndView runningQueueManagement() {
        ModelAndView modelAndView = new ModelAndView("runningqueue_management");
        modelAndView.addObject("queue", "running");
        return modelAndView;
    }


    @RequestMapping("/queryQueueByName")
    @ResponseBody
    public Object queryQueueByName(QueueQueryParam queryParam) {
        Object data = null;
        String name = queryParam.getName();
        if (StringUtils.equalsIgnoreCase(name, "fail")) {
            data = masterActor.retrievedFailQueueRegistrations(queryParam);
        } else if (StringUtils.equalsIgnoreCase(name, "success")) {
            data = masterActor.retrievedSuccessQueueRegistrations(queryParam);
        } else if (StringUtils.equalsIgnoreCase(name, "waiting")) {
            data = masterActor.retrievedWaitingQueueRegistrations(queryParam);
        } else if (StringUtils.equalsIgnoreCase(name, "running")) {
            data = masterActor.retrievedRunningQueueRegistrations(queryParam);
        }
        return data;
    }

    @RequestMapping(value = "/viewCrawlerResult", method = RequestMethod.POST)
    @ResponseBody
    public Object viewCrawlerResult(@RequestBody Map map) {
        String pageUrl = MapUtils.getString(map, "pageUrl");
        String taskId = MapUtils.getString(map, "taskId");
        Assert.notNull(pageUrl,"抓取链接不能为空！");
        Assert.notNull(taskId,"任务ID不能为空！");

        return resultRetrivalService.retrivalByTaskId(UrlUtils.getDomain(pageUrl).replace(".", "_"), taskId);
    }

    @RequestMapping(value = "/uploadFile",method = RequestMethod.POST)
    @ResponseBody
    public Object upload(HttpServletRequest request, HttpServletResponse response, @RequestParam("files") MultipartFile[] files)
    {
        MultipartFile file = files[0];
        if(file==null) return false;
        String filename = file.getOriginalFilename();
        if (filename == null || "".equals(filename))
        {
            return null;
        }
        Map map;
        if(StringUtils.endsWithAny(file.getOriginalFilename(),".csv",".txt"))
            map = taskService.insertCSV(file);
        else
            map = taskService.insertExcel(file);
        return map;
    }



}
