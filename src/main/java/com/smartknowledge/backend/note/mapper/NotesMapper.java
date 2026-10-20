package com.smartknowledge.backend.note.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.smartknowledge.backend.note.entity.Notes;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface NotesMapper extends BaseMapper<Notes> {}
