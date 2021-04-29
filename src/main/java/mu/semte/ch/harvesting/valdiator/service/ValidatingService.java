package mu.semte.ch.harvesting.valdiator.service;

import lombok.extern.slf4j.Slf4j;
import mu.semte.ch.harvesting.valdiator.Constants;
import mu.semte.ch.lib.dto.DataContainer;
import mu.semte.ch.lib.dto.Task;
import mu.semte.ch.lib.utils.ModelUtils;
import org.apache.jena.rdf.model.Model;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class ValidatingService {
  private final ShaclService shaclService;
  private final TaskService taskService;
  private final XlsReportService xlsReportService;

  public ValidatingService(ShaclService shaclService,
                           TaskService taskService,
                           XlsReportService xlsReportService) {
    this.shaclService = shaclService;
    this.taskService = taskService;
    this.xlsReportService = xlsReportService;
  }

  public void runValidatePipeline(Task task) {
    var inputContainer = taskService.selectInputContainer(task).get(0);
    var importedTriples = taskService.loadImportedTriples(inputContainer.getGraphUri());

    var fileContainer = DataContainer.builder().build();

    var report = writeValidationReport(task, fileContainer, importedTriples);

    // write xls report
    xlsReportService.writeReport(task, report, fileContainer);

    // import validation report
    var reportGraph = "%s/%s".formatted(Constants.VALIDATING_GRAPH_PREFIX, task.getId());
    taskService.importTriples(reportGraph, report);

    // append result graph
    var resultContainer = DataContainer.builder()
                                       .graphUri(inputContainer.getGraphUri())
                                       .validationGraphUri(reportGraph)
                                       .build();

    taskService.appendTaskResultGraph(task, resultContainer);
  }


  private Model writeValidationReport(Task task, DataContainer fileContainer, Model importedTriples) {
    log.debug("generate validation reports...");
    var report = shaclService.validate(importedTriples.getGraph());
    log.debug("triples conforms: {}", report.conforms());
    var reportModel = ModelUtils.replaceAnonNodes(report.getModel());
    var dataContainer = fileContainer.toBuilder()
                                     .graphUri(taskService.writeTtlFile(task.getGraph(), reportModel, "validation-report.ttl"))
                                     .build();
    taskService.appendTaskResultFile(task, dataContainer);
    return reportModel;
  }

}
