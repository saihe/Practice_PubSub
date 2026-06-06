package com.example.pubsub.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.example.pubsub.service.TaskView;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.server.RepresentationModelAssembler;
import org.springframework.stereotype.Component;

/**
 * {@link TaskView} を HATEOAS リンク付きの {@link EntityModel} へ変換するアセンブラ。
 *
 * <p>フロントエンドはこれらの {@code _links} を辿るだけでよく、
 * 個別のエンドポイントURL文字列を知る必要がない。</p>
 *
 * <ul>
 *   <li>{@code self}     … このタスクの取得 (GET)</li>
 *   <li>{@code statuses} … 積み上がったステータス履歴 (GET)</li>
 *   <li>{@code cancel}   … 中止 (POST)。終端でない場合のみ付与。</li>
 * </ul>
 */
@Component
public class TaskModelAssembler implements RepresentationModelAssembler<TaskView, EntityModel<TaskView>> {

    @Override
    public EntityModel<TaskView> toModel(TaskView view) {
        EntityModel<TaskView> model = EntityModel.of(view,
                linkTo(methodOn(TaskController.class).getTask(view.taskId())).withSelfRel(),
                linkTo(methodOn(TaskController.class).getStatuses(view.taskId())).withRel("statuses"));

        // 終端でないタスクのみ「中止」リンクを提供 → フロントはリンク有無でボタンの有効/無効を判断できる。
        if (!view.terminal()) {
            model.add(linkTo(methodOn(TaskController.class).cancelTask(view.taskId())).withRel("cancel"));
        }
        return model;
    }
}
