package br.eti.hpds.fastFurious.controller;

import br.eti.hpds.fastFurious.domain.dto.EstornoDTO;
import br.eti.hpds.fastFurious.domain.dto.SolicitarEstornoDTO;
import br.eti.hpds.fastFurious.service.EstornoService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Estorno: operação de gerente/caixa. Todas as chamadas exigem o header X-Gerente-Token.
 * O totem não deve chamar estes endereços.
 */
@RestController
public class EstornoController {

    private static final String TOKEN = "X-Gerente-Token";
    private static final String OPERADOR = "X-Operador";

    private final EstornoService estornos;
    private final GerenteAuth auth;

    public EstornoController(EstornoService estornos, GerenteAuth auth) {
        this.estornos = estornos;
        this.auth = auth;
    }

    @PostMapping("/pedido/{pedidoID}/estorno")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Solicita o estorno de um pedido pago",
            description = "Devolve o valor total. Cartão: a maquininha executa; PIX pela Cielo: por API; dinheiro: manual")
    public EstornoDTO solicitar(
            @PathVariable Long pedidoID,
            @RequestHeader(value = TOKEN, required = false) String token,
            @RequestHeader(value = OPERADOR, required = false) String operador,
            @Valid @RequestBody SolicitarEstornoDTO dto) {
        auth.exigir(token);
        return EstornoDTO.de(estornos.solicitar(pedidoID, dto.motivo(), operador));
    }

    @GetMapping("/pedido/{pedidoID}/estornos")
    @Operation(summary = "Estornos de um pedido")
    public List<EstornoDTO> listar(
            @PathVariable Long pedidoID,
            @RequestHeader(value = TOKEN, required = false) String token) {
        auth.exigir(token);
        return estornos.listarPorPedido(pedidoID).stream().map(EstornoDTO::de).toList();
    }

    @GetMapping("/estornos/{id}")
    @Operation(summary = "Situação de um estorno")
    public ResponseEntity<EstornoDTO> buscar(
            @PathVariable String id,
            @RequestHeader(value = TOKEN, required = false) String token) {
        auth.exigir(token);
        return estornos.buscar(id)
                .map(e -> ResponseEntity.ok(EstornoDTO.de(e)))
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/estornos/{id}/concluir")
    @Operation(summary = "Confirma um estorno manual",
            description = "Use depois de devolver o dinheiro (ou de cancelar no terminal). Vale para PENDENTE_MANUAL e EM_ANDAMENTO")
    public EstornoDTO concluir(
            @PathVariable String id,
            @RequestHeader(value = TOKEN, required = false) String token) {
        auth.exigir(token);
        return EstornoDTO.de(estornos.concluirManual(id));
    }
}
