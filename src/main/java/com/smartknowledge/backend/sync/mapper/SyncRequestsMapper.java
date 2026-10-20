package com.smartknowledge.backend.sync.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartknowledge.backend.sync.entity.SyncRequests;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface SyncRequestsMapper extends BaseMapper<SyncRequests> {}
