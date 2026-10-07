package br.eti.hpds.fastFurious.controller;

import br.eti.hpds.fastFurious.domain.dto.AtualizaStatusDTO;
import br.eti.hpds.fastFurious.domain.model.Pedido;
import br.eti.hpds.fastFurious.domain.model.StatusPedido;
import br.eti.hpds.fastFurious.domain.repository.PedidoRepository;
import br.eti.hpds.fastFurious.service.PedidoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PedidoController {

    @Autowired
    private PedidoRepository pedidoRepository;

    @Autowired
    private PedidoService pedidoService;

    @GetMapping("/pedido")
    @Operation(summary = "Lista os Pedidos", description = "Retorna todos os Pedidos")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved"),
        @ApiResponse(responseCode = "404", description = "Not found - The product was not found")}
    )
    public List<Pedido> listar() {
        return pedidoRepository.findAll();
    }

    @GetMapping("/pedido/{pedidoID}")
    @Operation(summary = "Lista os Pedidos por ID", description = "Retorna os Pedidos de um determinado ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved"),
        @ApiResponse(responseCode = "404", description = "Not found - The product was not found")}
    )
    public ResponseEntity<Pedido> buscarById(@PathVariable Long pedidoID) {

        Optional<Pedido> pedido = pedidoRepository.findById(pedidoID);

        if (pedido.isPresent()) {
            return ResponseEntity.ok(pedido.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/pedido/status/{status}")
    @Operation(summary = "Lista os Pedidos por status", description = "Retorna os Pedidos de um determinado status")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully retrieved"),
        @ApiResponse(responseCode = "404", description = "Not found - The product was not found")}
    )
    public ResponseEntity<List<Pedido>> buscarByStatus(@PathVariable String status) {
        
            StatusPedido statusEnum = StatusPedido.valueOf(status.toUpperCase());

            List<Pedido> lista = pedidoRepository.findByStatus(statusEnum);

            if (lista.isEmpty()) {
                return ResponseEntity.noContent().build();
            }

            return ResponseEntity.ok(lista);
    }

    @PostMapping("/pedido")
    @Operation(summary = "Publica um determinada Pedido", description = "Publicação de um Pedido na base de dados")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Successfully posted"),
        @ApiResponse(responseCode = "422", description = "The format is correct, but the data failed the business rule")}
    )
    @ResponseStatus(HttpStatus.CREATED)
    public Pedido criar(@Valid @RequestBody Pedido pedido) {

        return pedidoService.criar(pedido);
    }

    @PutMapping("/pedido/atualizaStatus/{pedidoID}")
    @Operation(summary = "Atualiza o status de um Pedido determinado ", description = "Atualização de um status na base de dados")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully updated"),
        @ApiResponse(responseCode = "404", description = "Not found - The product was not found")}
    )
    public ResponseEntity<Pedido> atualizarStatus(@Valid @PathVariable Long pedidoID,
            @RequestBody AtualizaStatusDTO atualizaStatusDTO) {

        Optional<Pedido> optPedido = pedidoService.atualizarStatus(pedidoID, atualizaStatusDTO.status());

        if (optPedido.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(optPedido.get());

    }

    @PutMapping("/pedido/confirmarPagamentoDinheiro/{pedidoID}")
    @Operation(summary = "Confirma pagamento em dinheiro", description = "O atendente confirma o recebimento; o pedido passa de AGUARDANDO_PAGAMENTO para ABERTO")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Payment confirmed"),
        @ApiResponse(responseCode = "404", description = "Not found - The order was not found")}
    )
    public ResponseEntity<Pedido> confirmarPagamentoDinheiro(@PathVariable Long pedidoID) {
        return pedidoService.confirmarPagamentoDinheiro(pedidoID)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/pedido/{pedidoID}")
    @Operation(summary = "Atualiza um determinado Pedido ", description = "Atualização de um Pedido na base de dados")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Successfully updated"),
        @ApiResponse(responseCode = "404", description = "Not found - The product was not found")}
    )
    public ResponseEntity<Pedido> atualizar(@Valid @PathVariable Long pedidoID,
            @RequestBody Pedido pedido) {

        if (!pedidoRepository.existsById(pedidoID)) {
            return ResponseEntity.notFound().build();
        }

        pedido.setId(pedidoID);
        Optional<Pedido> optPedido = pedidoService.salvar(pedido);
        if (optPedido.isPresent()) {
            return ResponseEntity.ok(optPedido.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/pedido/{pedidoID}")
    public ResponseEntity<Void> excluir(@PathVariable Long pedidoID) {
        if (!pedidoRepository.existsById(pedidoID)) {
            return ResponseEntity.notFound().build();
        }

        pedidoService.excluir(pedidoID);
        return ResponseEntity.noContent().build();
    }

}