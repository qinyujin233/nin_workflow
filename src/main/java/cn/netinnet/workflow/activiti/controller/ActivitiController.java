package cn.netinnet.workflow.activiti.controller;

import cn.netinnet.common.util.DateUtil;
import cn.netinnet.workflow.activiti.dao.FormJsonMapper;
import cn.netinnet.workflow.activiti.domain.FormJson;
import cn.netinnet.workflow.common.global.HttpResultEntry;
import cn.netinnet.workflow.user.domain.WorkflowUser;
import cn.netinnet.workflow.util.FileUtil;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.activiti.bpmn.converter.BpmnXMLConverter;
import org.activiti.bpmn.model.BpmnModel;
import org.activiti.engine.RepositoryService;
import org.activiti.engine.RuntimeService;
import org.activiti.engine.TaskService;
import org.activiti.engine.repository.Deployment;
import org.activiti.engine.repository.Model;
import org.activiti.engine.runtime.ProcessInstance;
import org.activiti.engine.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.util.ResourceUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName ActivitiController
 * @Description
 * @Author yuyb
 * @Date 2020/3/12 17:21
 */
@Controller
@RequestMapping("/model")
public class ActivitiController {
	
	private static final Logger log = LoggerFactory.getLogger(ActivitiController.class);
	 
    @Resource
    RepositoryService repositoryService;
    @Resource
    RuntimeService runtimeService;
    @Resource
    TaskService taskService;
    @Resource
    FormJsonMapper formJsonMapper;

    @RequestMapping(value = "add")
    public ModelAndView index(HttpSession session){
        ModelAndView mv = new ModelAndView();
        mv.setViewName("bpmn/add");
        return mv;
    }

    @RequestMapping(value = "purchase")
    public ModelAndView purchase(HttpSession session){
        ModelAndView mv = new ModelAndView();
        mv.setViewName("model/cgd");
        return mv;
    }

    @RequestMapping(value = "excel")
    public ModelAndView spreadSheet(HttpSession session){
        ModelAndView mv = new ModelAndView();
        mv.setViewName("model/excel");
        return mv;
    }

    @RequestMapping(value = "modelDetail")
    public ModelAndView modelDetail(String type){
        Long excelId = 1L;
        if("purchase".equals(type)){
            excelId = 1L;
        }else if("material".equals(type)){
            excelId = 2L;
        }
        FormJson formJson = formJsonMapper.selectByPrimaryKey(excelId);
        ModelAndView mv = new ModelAndView();
        mv.addObject("excelData", formJson.getJsonData());
        mv.addObject("modelName", type);
        mv.setViewName("model/excel");
        return mv;
    }

    @RequestMapping(value="getExcelJson")
    public HttpResultEntry getExcelJson(HttpServletRequest request, String fileName){
        String path = System.getProperty("user.dir")+"\\src\\main\resources\\data";
        fileName = path + File.separator + fileName;
        String excelJson = FileUtil.readJsonFile(fileName);
        return HttpResultEntry.ok("", excelJson);
    }

    @RequestMapping(value="setExcelJson")
    public HttpResultEntry setExcelJson(HttpServletRequest request, Long excelId, String excelJson){
        /*String path = System.getProperty("user.dir")+"\\src\\main\resources\\data";
        String filePath = path + File.separator + fileName + ".text";
        try {
            FileUtil.writeFile(filePath, excelJson);
        } catch (IOException e) {
            e.printStackTrace();
        }*/
        FormJson formJson = new FormJson();
        formJson.setId(excelId);
        formJson.setJsonData(excelJson);
        formJsonMapper.insertSelective(formJson);
        return HttpResultEntry.ok();
    }

    /**
     * 获取所有模型
     */
    @RequestMapping(value="modelList")
    public HttpResultEntry modelList(HttpServletRequest request){
        return HttpResultEntry.ok("", repositoryService.createModelQuery().list());
    }

    /**
     * 发布模型为流程定义
     */
    @RequestMapping("/deploy")
    @ResponseBody
    public Object deploy(String modelId) throws Exception {
 
        //获取模型
        Model modelData = repositoryService.getModel(modelId);
        byte[] bytes = repositoryService.getModelEditorSource(modelData.getId());
        if (bytes == null) {
            return "模型数据为空，请先设计流程并成功保存，再进行发布。";
        }
        JsonNode modelNode = new ObjectMapper().readTree(bytes);
        //BpmnModel model = new BpmnJsonConverter().convertToBpmnModel(modelNode);
        BpmnModel model = new BpmnModel();
        if(model.getProcesses().size()==0){
            return "数据模型不符要求，请至少设计一条主线流程。";
        }
        byte[] bpmnBytes = new BpmnXMLConverter().convertToXML(model);
        //发布流程
        String processName = modelData.getName() + ".bpmn20.xml";
        Deployment deployment = repositoryService.createDeployment()
                .name(modelData.getName())
                .addString(processName, new String(bpmnBytes, "UTF-8"))
                .deploy();
        modelData.setDeploymentId(deployment.getId());
        repositoryService.saveModel(modelData);
 
        return "SUCCESS";
    }
 
    /**
     *  启动流程
     */
    @RequestMapping("/start")
    @ResponseBody
    public Object startProcess(String keyName) {
        //设置assignee的取值，用户可以在界面上设置流程的执行人
        Map<String, Object> map = new HashMap<>(5);
        map.put("assignee0", "张三");
        map.put("assignee1", "李四");
        map.put("assignee2", "王五");
        ProcessInstance process = runtimeService.startProcessInstanceByKey(keyName, map);
        return process.getId() + " : " + process.getProcessDefinitionId();
    }
 
    /**
     *  提交任务
     */
    @RequestMapping("/run")
    @ResponseBody
    public Object run(String processInstanceId) {
        Task task = taskService.createTaskQuery().processInstanceId(processInstanceId).singleResult();
        log.info("task {} find ", task.getId());
        taskService.complete(task.getId());
        return "SUCCESS";
    }

}
