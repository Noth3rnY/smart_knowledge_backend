package com.smartknowledge.backend.backup.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartknowledge.backend.backup.entity.BackupItems;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BackupItemsMapper extends BaseMapper<BackupItems> {}
