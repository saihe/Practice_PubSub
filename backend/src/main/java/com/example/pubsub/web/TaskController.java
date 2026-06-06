package com.example.pubsub.web;

import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.linkTo;
import static org.springframework.hateoas.server.mvc.WebMvcLinkBuilder.methodOn;

import com.example.pubsub.service.TaskService;
import com.example.pubsub.service.TaskView;
import java.util.List;
import org.springframework.hateoas.CollectionModel;
import org.springframework.hateoas.EntityModel;
import org.springframework.hateoas.IanaLinkRelations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * タスク関連の REST エンドポイント(HATEOAS 対応)。
 *
 * <p>{@code /api/tasks} 配下で公開される。
 * フロントエンドはルート({@code GET /api})から辿るリンクのみを利用し、
 * 本クラスのパス文字列を直接知る必要はない。</p>
 */
@RestController
@RequestMapping("/api/tasks")
public class TaskController {

    private final TaskService taskService;
    private final TaskModelAssembler assembler;

    public TaskController(TaskService taskService, TaskModelAssembler assembler) {
        this.taskService = taskService;
        this.assembler = assembler;
    }

    /** タスク一覧(現在状態に畳み込み済み)。collection に {@code create} リンクを付与。 */
    @GetMapping
    public CollectionModel<EntityModel<TaskView>> listTasks() {
        List<EntityModel<TaskView>> tasks = taskService.listTasks().stream()
                .map(assembler::toModel)
                .toList();

        return CollectionModel.of(tasks,
                linkTo(methodOn(TaskController.class).listTasks()).withSelfRel(),
                // 「タスク実行(追加)」が POST する先。フロントは rel=create を辿る。
                linkTo(methodOn(TaskController.class).createTask()).withRel("create"));
    }

    /** タスクをキューに追加する(タスク実行ボタン)。 */
    @PostMapping
    public ResponseEntity<EntityModel<TaskView>> createTask() {
        TaskView view = taskService.queueTask();
        EntityModel<TaskView> model = assembler.toModel(view);
        return ResponseEntity
                .created(model.getRequiredLink(IanaLinkRelations.SELF).toUri())
                .body(model);
    }

    /** 単一タスクの取得。 */
    @GetMapping("/{id}")
    public EntityModel<TaskView> getTask(@PathVariable String id) {
        return taskService.getTask(id)
                .map(assembler::toModel)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found: " + id));
    }

    /** タスク中止(中止ボタン)。終端タスク・未知IDは 409/404。 */
    @PostMapping("/{id}/cancel")
    public ResponseEntity<EntityModel<TaskView>> cancelTask(@PathVariable String id) {
        if (taskService.getTask(id).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found: " + id);
        }
        return taskService.requestCancel(id)
                .map(view -> ResponseEntity.accepted().body(assembler.toModel(view)))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "task is already finished: " + id));
    }

    /** ステータス履歴(append-only に積まれたスタック)。 */
    @GetMapping("/{id}/statuses")
    public CollectionModel<StatusEventDto> getStatuses(@PathVariable String id) {
        List<StatusEventDto> events = taskService.getStatusHistory(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "task not found: " + id))
                .stream()
                .map(StatusEventDto::from)
                .toList();

        return CollectionModel.of(events,
                linkTo(methodOn(TaskController.class).getStatuses(id)).withSelfRel(),
                linkTo(methodOn(TaskController.class).getTask(id)).withRel("task"));
    }
}
