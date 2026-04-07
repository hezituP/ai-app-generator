package com.hezitu.heaicodemother.service;

import com.hezitu.heaicodemother.model.dto.app.AppAddRequest;
import com.hezitu.heaicodemother.model.dto.app.AppQueryRequest;
import com.hezitu.heaicodemother.model.entity.App;
import com.hezitu.heaicodemother.model.entity.User;
import com.hezitu.heaicodemother.model.vo.AgentStreamEvent;
import com.hezitu.heaicodemother.model.vo.AppProjectSnapshotVO;
import com.hezitu.heaicodemother.model.vo.AppVO;
import com.mybatisflex.core.query.QueryWrapper;
import com.mybatisflex.core.service.IService;
import reactor.core.publisher.Flux;

import java.io.Serializable;
import java.util.List;

public interface AppService extends IService<App> {

    Flux<AgentStreamEvent> chatToGenCode(Long appId, String message, User loginUser);

    Long createApp(AppAddRequest appAddRequest, User loginUser);

    String deployApp(Long appId, User loginUser);

    void generateAppScreenshotAsync(Long appId, String appUrl);

    AppProjectSnapshotVO getProjectSnapshot(Long appId, User loginUser);

    byte[] downloadAppCode(Long appId, User loginUser);

    boolean deleteApp(Serializable id, User loginUser);

    AppVO getAppVO(App app);

    List<AppVO> getAppVOList(List<App> appList);

    QueryWrapper getQueryWrapper(AppQueryRequest appQueryRequest);
}
