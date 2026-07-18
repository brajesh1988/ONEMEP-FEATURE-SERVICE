package com.netlink.onemep_feature.project.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netlink.onemep_feature.common.adaptor.ApiResponseAdaptor;
import com.netlink.onemep_feature.project.dto.ProjectDto;
import com.netlink.onemep_feature.project.service.ProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * HTTP-contract test for the project controller: create must answer {@code 201 Created} and the
 * dedicated priority endpoint must be wired (ONEMEP-13/14). Service is mocked; this asserts the web
 * layer's status codes and routing.
 */
@ExtendWith(MockitoExtension.class)
class ProjectControllerStatusTest {

  @Mock private ProjectService projectService;
  private MockMvc mockMvc;
  private final ApiResponseAdaptor adaptor = new ApiResponseAdaptor();

  @BeforeEach
  void setUp() {
    ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    mockMvc =
        MockMvcBuilders.standaloneSetup(new ProjectController(projectService))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
            .build();
  }

  @Test
  void createProject_returns201() throws Exception {
    when(projectService.create(any(ProjectDto.CreateRequest.class)))
        .thenReturn(adaptor.success("Project created successfully."));

    mockMvc
        .perform(
            post("/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Apollo\",\"categoryId\":1}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.success").value(true));
  }

  @Test
  void createProject_missingName_returns400() throws Exception {
    mockMvc
        .perform(
            post("/projects").contentType(MediaType.APPLICATION_JSON).content("{\"categoryId\":1}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updatePriority_returns200() throws Exception {
    when(projectService.updatePriority(eq(1L), eq("HIGH")))
        .thenReturn(adaptor.success("Project priority updated successfully."));

    mockMvc
        .perform(patch("/projects/1/priority").param("priority", "HIGH"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true));
  }
}
