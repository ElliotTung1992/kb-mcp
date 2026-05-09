package com.enterprise.kb.mcp.tools.ielts;

import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.annotation.Tool;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class IeltsToolReturnTypeTest {

    @Test
    void allIeltsToolsReturnStructuredObjects() {
        List<Method> toolMethods = List.of(
                        IeltsWordTool.class,
                        IeltsContentTool.class,
                        IeltsStudyTool.class,
                        IeltsTrainingTool.class
                ).stream()
                .flatMap(toolClass -> Arrays.stream(toolClass.getDeclaredMethods()))
                .filter(method -> method.isAnnotationPresent(Tool.class))
                .toList();

        assertThat(toolMethods).isNotEmpty();
        assertThat(toolMethods)
                .allSatisfy(method -> assertThat(method.getReturnType())
                        .as("%s#%s should return a structured object",
                                method.getDeclaringClass().getSimpleName(),
                                method.getName())
                        .isEqualTo(Map.class));
    }
}
