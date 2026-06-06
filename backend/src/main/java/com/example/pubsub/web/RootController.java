package com.example.pubsub.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import org.springframework.hateoas.RepresentationModel;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * API のエントリポイント(ルート)。
 *
 * <p>フロントエンドが唯一直接知るURLがこの {@code GET /api}。
 * ここから {@code tasks} リンクを辿ることで以降のエンドポイントを発見する(HATEOAS)。</p>
 */
@RestController
public class RootController {

    @GetMapping("/api")
    public RepresentationModel<?> root() {
        RepresentationModel<?> model = new RepresentationModel<>();
        model.add(linkTo(methodOn(RootController.class).root()).withSelfRel());
        model.add(linkTo(methodOn(TaskController.class).listTasks()).withRel("tasks"));
        return model;
    }
}
