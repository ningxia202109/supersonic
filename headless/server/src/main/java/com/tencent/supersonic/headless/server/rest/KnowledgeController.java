package com.tencent.supersonic.headless.server.rest;


import com.github.pagehelper.PageInfo;
import com.tencent.supersonic.auth.api.authentication.pojo.User;
import com.tencent.supersonic.auth.api.authentication.utils.UserHolder;
import com.tencent.supersonic.headless.api.pojo.request.DictItemFilter;
import com.tencent.supersonic.headless.api.pojo.request.DictItemReq;
import com.tencent.supersonic.headless.api.pojo.request.DictSingleTaskReq;
import com.tencent.supersonic.headless.api.pojo.request.DictValueReq;
import com.tencent.supersonic.headless.api.pojo.response.DictItemResp;
import com.tencent.supersonic.headless.api.pojo.response.DictTaskResp;
import com.tencent.supersonic.headless.api.pojo.response.DictValueResp;
import com.tencent.supersonic.headless.server.task.MetaEmbeddingTask;
import com.tencent.supersonic.headless.server.service.DictConfService;
import com.tencent.supersonic.headless.server.service.DictTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.util.List;


@RestController
@RequestMapping("/api/semantic/knowledge")
public class KnowledgeController {

    @Autowired
    private DictTaskService taskService;

    @Autowired
    private DictConfService confService;

    @Autowired
    private MetaEmbeddingTask embeddingTask;

    /**
     * addDictConf-新增item的字典配置
     * Add configuration information for dictionary entries
     *
     * @param dictItemReq
     */
    @PostMapping("/conf")
    public DictItemResp addDictConf(@RequestBody @Valid DictItemReq dictItemReq,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        User user = UserHolder.findUser(request, response);
        return confService.addDictConf(dictItemReq, user);
    }

    /**
     * editDictConf-编辑item的字典配置
     * Edit configuration information for dictionary entries
     *
     * @param dictItemReq
     */
    @PutMapping("/conf")
    public DictItemResp editDictConf(@RequestBody @Valid DictItemReq dictItemReq,
                                     HttpServletRequest request,
                                     HttpServletResponse response) {
        User user = UserHolder.findUser(request, response);
        return confService.editDictConf(dictItemReq, user);
    }

    /**
     * queryDictConf-查询item的字典配置
     * query configuration information for dictionary entries
     *
     * @param filter
     */
    @PostMapping("/conf/query")
    public List<DictItemResp> queryDictConf(@RequestBody @Valid DictItemFilter filter,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        User user = UserHolder.findUser(request, response);
        return confService.queryDictConf(filter, user);
    }


    /**
     * addDictTask-实时导入一个item的字典数据
     * write specific item values to the knowledge base
     *
     * @param taskReq
     */
    @PostMapping("/task")
    public Long addDictTask(@RequestBody DictSingleTaskReq taskReq,
                            HttpServletRequest request,
                            HttpServletResponse response) {
        User user = UserHolder.findUser(request, response);
        return taskService.addDictTask(taskReq, user);
    }

    /**
     * deleteDictTask-实时删除某一个item的字典数据
     * remove specific item values from the knowledge base
     *
     * @param taskReq
     */
    @PutMapping("/task/delete")
    public Long deleteDictTask(@RequestBody DictSingleTaskReq taskReq,
                               HttpServletRequest request,
                               HttpServletResponse response) {
        User user = UserHolder.findUser(request, response);
        return taskService.deleteDictTask(taskReq, user);
    }

    /**
     * dailyDictTask-手动离线更新所有字典
     */
    @PutMapping("/task/all")
    public Boolean dailyDictTask(
            HttpServletRequest request,
            HttpServletResponse response) {
        return taskService.dailyDictTask();
    }

    /**
     * queryLatestDictTask-返回最新的字典任务执行情况
     *
     * @param taskReq
     */
    @PostMapping("/task/search")
    public DictTaskResp queryLatestDictTask(@RequestBody DictSingleTaskReq taskReq,
                                            HttpServletRequest request,
                                            HttpServletResponse response) {
        User user = UserHolder.findUser(request, response);
        return taskService.queryLatestDictTask(taskReq, user);
    }

    @GetMapping("/meta/embedding/reload")
    public Object reloadMetaEmbedding() {
        embeddingTask.reloadMetaEmbedding();
        return true;
    }

    @GetMapping("/embedding/persistFile")
    public Object executePersistFileTask() {
        embeddingTask.executePersistFileTask();
        return true;
    }

    /**
     * queryDictValue-返回字典的数据
     *
     * @param dictValueReq
     */
    @PostMapping("/dict/data")
    public PageInfo<DictValueResp> queryDictValue(@RequestBody @Valid DictValueReq dictValueReq,
                                                  HttpServletRequest request,
                                                  HttpServletResponse response) {
        User user = UserHolder.findUser(request, response);
        return taskService.queryDictValue(dictValueReq, user);
    }

    /**
     * queryDictFilePath-返回字典文件的路径
     *
     * @param dictValueReq
     */
    @PostMapping("/dict/file")
    public String queryDictFilePath(@RequestBody @Valid DictValueReq dictValueReq,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        User user = UserHolder.findUser(request, response);
        return taskService.queryDictFilePath(dictValueReq, user);
    }

}
