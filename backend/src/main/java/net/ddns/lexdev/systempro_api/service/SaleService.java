package net.ddns.lexdev.systempro_api.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.persistence.EntityNotFoundException;
import net.ddns.lexdev.systempro_api.config.CpfCnpjNormalizer;
import net.ddns.lexdev.systempro_api.config.FiscalProperties;
import net.ddns.lexdev.systempro_api.domain.Client;
import net.ddns.lexdev.systempro_api.domain.FiscalDocument;
import net.ddns.lexdev.systempro_api.domain.FiscalEstablishment;
import net.ddns.lexdev.systempro_api.domain.FiscalEvent;
import net.ddns.lexdev.systempro_api.domain.FiscalProductProfile;
import net.ddns.lexdev.systempro_api.domain.Product;
import net.ddns.lexdev.systempro_api.domain.Sale;
import net.ddns.lexdev.systempro_api.domain.SaleItem;
import net.ddns.lexdev.systempro_api.domain.SalePayment;
import net.ddns.lexdev.systempro_api.domain.User;
import net.ddns.lexdev.systempro_api.dto.FiscalDocumentResponseDto;
import net.ddns.lexdev.systempro_api.dto.SaleCreateRequestDto;
import net.ddns.lexdev.systempro_api.dto.SaleItemRequestDto;
import net.ddns.lexdev.systempro_api.dto.SalePaymentRequestDto;
import net.ddns.lexdev.systempro_api.dto.SaleResponseDto;
import net.ddns.lexdev.systempro_api.enums.FiscalDocumentStatus;
import net.ddns.lexdev.systempro_api.enums.FiscalEmissionType;
import net.ddns.lexdev.systempro_api.enums.FiscalEventType;
import net.ddns.lexdev.systempro_api.enums.PaymentMethod;
import net.ddns.lexdev.systempro_api.enums.ProductStatus;
import net.ddns.lexdev.systempro_api.enums.SaleStatus;
import net.ddns.lexdev.systempro_api.exception.BusinessException;
import net.ddns.lexdev.systempro_api.exception.FiscalConfigurationException;
import net.ddns.lexdev.systempro_api.fiscal.NfceIssueResult;
import net.ddns.lexdev.systempro_api.fiscal.SefazNfceGateway;
import net.ddns.lexdev.systempro_api.fiscal.contingency.NfceContingencyPolicy;
import net.ddns.lexdev.systempro_api.repository.ClientRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalDocumentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEventRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalEstablishmentRepository;
import net.ddns.lexdev.systempro_api.repository.FiscalProductProfileRepository;
import net.ddns.lexdev.systempro_api.repository.ProductRepository;
import net.ddns.lexdev.systempro_api.repository.SaleRepository;

@Service
public class SaleService {

    private final SaleRepository saleRepository;
    private final ProductRepository productRepository;
    private final FiscalProductProfileRepository profileRepository;
    private final FiscalEstablishmentRepository establishmentRepository;
    private final ClientRepository clientRepository;
    private final FiscalDocumentRepository fiscalDocumentRepository;
    private final FiscalEventRepository fiscalEventRepository;
    private final CurrentUserProvider currentUserProvider;
    private final FiscalEstablishmentService fiscalEstablishmentService;
    private final SefazNfceGateway gateway;
    private final FiscalProperties fiscalProperties;
    private final IbsCbsSaleSnapshotService rtcSnapshotService;
    private final NfceContingencyPolicy contingencyPolicy;
    private final StockService stockService;

    private static final ZoneId FISCAL_ZONE = ZoneId.of("America/Sao_Paulo");

    public SaleService(
        SaleRepository saleRepository,
        ProductRepository productRepository,
        FiscalProductProfileRepository profileRepository,
        FiscalEstablishmentRepository establishmentRepository,
        ClientRepository clientRepository,
        FiscalDocumentRepository fiscalDocumentRepository,
        FiscalEventRepository fiscalEventRepository,
        CurrentUserProvider currentUserProvider,
        FiscalEstablishmentService fiscalEstablishmentService,
        SefazNfceGateway gateway,
        FiscalProperties fiscalProperties,
        IbsCbsSaleSnapshotService rtcSnapshotService,
        NfceContingencyPolicy contingencyPolicy,
        StockService stockService
    ) {
        this.saleRepository = saleRepository;
        this.productRepository = productRepository;
        this.profileRepository = profileRepository;
        this.establishmentRepository = establishmentRepository;
        this.clientRepository = clientRepository;
        this.fiscalDocumentRepository = fiscalDocumentRepository;
        this.fiscalEventRepository = fiscalEventRepository;
        this.currentUserProvider = currentUserProvider;
        this.fiscalEstablishmentService = fiscalEstablishmentService;
        this.gateway = gateway;
        this.fiscalProperties = fiscalProperties;
        this.rtcSnapshotService = rtcSnapshotService;
        this.contingencyPolicy = contingencyPolicy;
        this.stockService = stockService;
    }

    @Transactional
    public SaleResponseDto create(SaleCreateRequestDto dto) {
        FiscalEstablishment establishment = establishmentRepository.findByIdForUpdate(dto.fiscalEstablishmentId())
            .orElseThrow(() -> new EntityNotFoundException("Estabelecimento fiscal não encontrado."));
        Client client = dto.clientId() == null
            ? null
            : clientRepository.findByIdWithPerson(dto.clientId())
                .orElseThrow(() -> new EntityNotFoundException("Cliente não encontrado."));

        String consumerDocument = normalizeDocument(dto.consumerCpfCnpj());
        if (client != null) {
            String clientDocument = normalizeDocument(client.getPerson().getCpfCnpj());
            if (consumerDocument != null && !consumerDocument.equals(clientDocument)) {
                throw new BusinessException("O CPF/CNPJ informado para o consumidor não corresponde ao cliente selecionado.");
            }
            consumerDocument = clientDocument;
        }

        User user = currentUserProvider.requireUser();
        Sale sale = new Sale();
        sale.setFiscalEstablishment(establishment);
        sale.setClient(client);
        sale.setUser(user);
        sale.setSaleAt(Instant.now());
        sale.setConsumerCpfCnpj(consumerDocument);
        sale.setNote(dto.note() == null ? null : dto.note().trim());

        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal totalBeforeSaleDiscount = BigDecimal.ZERO;
        int itemNumber = 1;
        Map<SaleItem, FiscalProductProfile> rtcProfiles = new IdentityHashMap<>();

        for (SaleItemRequestDto request : dto.items()) {
            Product product = productRepository.findById(request.productId())
                .orElseThrow(() -> new EntityNotFoundException("Produto não encontrado: " + request.productId()));

            if (product.getStatus() != ProductStatus.ATIVO || !product.isActive()) {
                throw new BusinessException("O produto " + product.getCode() + " não está ativo para venda.");
            }

            FiscalProductProfile profile = profileRepository.findByProductId(product.getId())
                .orElseThrow(() -> new FiscalConfigurationException(
                    "O produto " + product.getCode() + " não possui perfil fiscal configurado."
                ));
            if (!profile.isActive()) {
                throw new FiscalConfigurationException("O perfil fiscal do produto " + product.getCode() + " está inativo.");
            }

            validateProductFiscalData(product, profile);

            BigDecimal unitPrice = money(request.unitPrice() == null ? product.getSalePrice() : request.unitPrice());
            if (unitPrice == null || unitPrice.compareTo(BigDecimal.ZERO) <= 0) {
                throw new BusinessException("O preço do produto " + product.getCode() + " precisa ser maior que zero.");
            }
            if (product.getMinimumSalePrice() != null
                && unitPrice.compareTo(product.getMinimumSalePrice()) < 0) {
                throw new BusinessException("O preço do produto " + product.getCode() + " está abaixo do preço mínimo configurado.");
            }

            BigDecimal quantity = request.quantity();
            BigDecimal itemDiscount = money(request.discount());
            BigDecimal gross = quantity.multiply(unitPrice).setScale(4, RoundingMode.HALF_UP);
            if (itemDiscount.compareTo(gross) > 0) {
                throw new BusinessException("O desconto do item do produto " + product.getCode() + " não pode superar o valor bruto do item.");
            }

            BigDecimal itemTotal = gross.subtract(itemDiscount).setScale(4, RoundingMode.HALF_UP);

            SaleItem item = new SaleItem();
            item.setItemNumber(itemNumber++);
            item.setProduct(product);
            item.setCodeSnapshot(product.getCode());
            item.setNameSnapshot(product.getName());
            item.setUnitSnapshot(product.getUnitOfMeasure());
            item.setGtinSnapshot(product.getGtin());
            item.setNcmSnapshot(product.getNcm());
            item.setCestSnapshot(product.getCest());
            item.setOriginSnapshot(product.getOrigin());
            item.setQuantity(quantity);
            item.setUnitPrice(unitPrice);
            item.setDiscount(itemDiscount);
            item.setTotal(itemTotal);
            item.setCfopSnapshot(profile.getCfop());
            item.setIcmsCstCsosnSnapshot(profile.getIcmsCstCsosn());
            item.setPisCstSnapshot(profile.getPisCst());
            item.setCofinsCstSnapshot(profile.getCofinsCst());
            item.setIcmsRate(profile.getIcmsRate());
            item.setPisRate(profile.getPisRate());
            item.setCofinsRate(profile.getCofinsRate());
            sale.addItem(item);
            rtcProfiles.put(item, profile);

            subtotal = subtotal.add(gross);
            totalBeforeSaleDiscount = totalBeforeSaleDiscount.add(itemTotal);
        }

        BigDecimal saleDiscount = money(dto.discount());
        if (saleDiscount.compareTo(totalBeforeSaleDiscount) > 0) {
            throw new BusinessException("O desconto total não pode superar o valor da venda após os descontos dos itens.");
        }
        allocateSaleDiscount(sale.getItems(), saleDiscount, totalBeforeSaleDiscount);

        LocalDate operationDate = sale.getSaleAt().atZone(FISCAL_ZONE).toLocalDate();
        for (SaleItem item : sale.getItems()) {
            rtcSnapshotService.apply(establishment, rtcProfiles.get(item), item, operationDate);
        }

        BigDecimal total = totalBeforeSaleDiscount.subtract(saleDiscount).setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalPaid = BigDecimal.ZERO;

        for (SalePaymentRequestDto request : dto.payments()) {
            BigDecimal amount = money(request.amount());
            validatePayment(request, amount);

            SalePayment payment = new SalePayment();
            payment.setPaymentMethod(request.paymentMethod());
            payment.setAmount(amount);
            payment.setCardBrand(blankToNull(request.cardBrand()));
            payment.setAuthorizationCode(blankToNull(request.authorizationCode()));
            sale.addPayment(payment);
            totalPaid = totalPaid.add(amount);
        }

        if (totalPaid.compareTo(total) < 0) {
            throw new BusinessException("O valor dos pagamentos é inferior ao total da venda.");
        }
        if (totalPaid.compareTo(total) > 0
            && sale.getPayments().stream().noneMatch(p -> p.getPaymentMethod() == PaymentMethod.DINHEIRO)) {
            throw new BusinessException("A venda possui troco, mas nenhum pagamento em dinheiro foi informado.");
        }

        sale.setSubtotal(subtotal.setScale(4, RoundingMode.HALF_UP));
        sale.setDiscount(saleDiscount);
        sale.setTotal(total);
        sale.setStatus(SaleStatus.AGUARDANDO_FISCAL);

        Sale saved = saleRepository.saveAndFlush(sale);
        stockService.registerSaleOut(saved);

        FiscalDocument document = new FiscalDocument();
        document.setSale(saved);
        document.setEstablishment(establishment);
        document.setSeries(establishment.getSeries());
        document.setNumber(establishment.getNextNumber());
        document.setStatus(FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO);
        document.setEmissionType(FiscalEmissionType.NORMAL);
        fiscalDocumentRepository.save(document);

        establishment.setNextNumber(establishment.getNextNumber() + 1L);

        return response(saved, document);
    }

    @Transactional
    public SaleResponseDto issue(Long saleId) {
        if (!fiscalProperties.enabled()) {
            throw new FiscalConfigurationException("A emissão fiscal está desabilitada no backend.");
        }

        Sale sale = saleRepository.findForFiscal(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Venda não encontrada."));
        FiscalDocument document = fiscalDocumentRepository.findBySaleId(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Documento fiscal da venda não encontrado."));

        if (document.getStatus() == FiscalDocumentStatus.AUTORIZADA) {
            return response(sale, document);
        }
        if (document.getStatus() == FiscalDocumentStatus.CANCELADA
            || document.getStatus() == FiscalDocumentStatus.CANCELAMENTO_PENDENTE) {
            throw new BusinessException("A NFC-e desta venda já possui um processo de cancelamento e não pode ser emitida novamente.");
        }
        if (document.getStatus() == FiscalDocumentStatus.PENDENTE_CONSULTA) {
            throw new BusinessException("A NFC-e está aguardando retorno da SEFAZ/MG. Consulte o documento antes de realizar nova tentativa.");
        }
        if (document.getStatus() == FiscalDocumentStatus.REJEITADA) {
            throw new BusinessException("A NFC-e desta venda foi rejeitada e este documento fiscal não pode ser emitido novamente.");
        }
        if (document.getStatus() == FiscalDocumentStatus.CONTINGENCIA
            || document.getEmissionType() == FiscalEmissionType.CONTINGENCIA_OFFLINE) {
            throw new BusinessException(
                "A NFC-e foi emitida em contingência offline. Utilize a transmissão da contingência; não gere uma nova emissão normal."
            );
        }

        FiscalEstablishment establishment = fiscalEstablishmentService.requireDetailed(document.getEstablishment().getId());
        fiscalEstablishmentService.assertReadyForEmission(establishment);

        NfceIssueResult result = gateway.authorize(establishment, sale, document);
        applyResult(document, result);
        sale.setStatus(saleStatusForFiscalResult(result.status()));
        return response(sale, document);
    }

    @Transactional
    public SaleResponseDto enterOfflineContingency(Long saleId, String justification) {
        if (!fiscalProperties.enabled()) {
            throw new FiscalConfigurationException("A emissão fiscal está desabilitada no backend.");
        }
        String normalizedJustification = justification == null ? null : justification.trim();
        if (normalizedJustification == null || normalizedJustification.length() < 15
            || normalizedJustification.length() > 256) {
            throw new BusinessException("A justificativa da contingência deve possuir entre 15 e 256 caracteres.");
        }

        Sale sale = saleRepository.findForFiscal(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Venda não encontrada."));
        FiscalDocument document = fiscalDocumentRepository.findBySaleId(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Documento fiscal da venda não encontrado."));
        contingencyPolicy.assertCanEnterOfflineContingency(document);

        FiscalEstablishment establishment = fiscalEstablishmentService.requireDetailed(document.getEstablishment().getId());
        fiscalEstablishmentService.assertReadyForEmission(establishment);

        Instant contingencyAt = Instant.now();
        document.setEmissionType(FiscalEmissionType.CONTINGENCIA_OFFLINE);
        document.setStatus(FiscalDocumentStatus.CONTINGENCIA);
        document.setContingencyAt(contingencyAt);
        document.setContingencyJustification(normalizedJustification);

        NfceIssueResult result = gateway.prepareOfflineContingency(
            establishment, sale, document, normalizedJustification
        );
        applyResult(document, result);
        sale.setStatus(saleStatusForFiscalResult(result.status()));
        return response(sale, document);
    }

    @Transactional
    public SaleResponseDto transmitOfflineContingency(Long saleId) {
        if (!fiscalProperties.enabled()) {
            throw new FiscalConfigurationException("A emissão fiscal está desabilitada no backend.");
        }
        Sale sale = saleRepository.findForFiscal(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Venda não encontrada."));
        FiscalDocument document = fiscalDocumentRepository.findBySaleId(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Documento fiscal da venda não encontrado."));
        contingencyPolicy.assertCanTransmitOfflineContingency(document);

        FiscalEstablishment establishment = fiscalEstablishmentService.requireDetailed(document.getEstablishment().getId());
        fiscalEstablishmentService.assertReadyForEmission(establishment);

        NfceIssueResult result = gateway.transmitOfflineContingency(establishment, document);
        applyResult(document, result);
        sale.setStatus(saleStatusForFiscalResult(result.status()));
        return response(sale, document);
    }

    @Transactional(readOnly = true)
    public String requireFiscalXml(Long saleId) {
        FiscalDocument document = fiscalDocumentRepository.findBySaleId(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Documento fiscal não encontrado."));
        if (document.getXml() == null || document.getXml().isBlank()) {
            throw new BusinessException("A NFC-e ainda não possui XML autorizado armazenado.");
        }
        return document.getXml();
    }

    @Transactional(readOnly = true)
    public SaleResponseDto findById(Long id) {
        Sale sale = saleRepository.findDetailed(id)
            .orElseThrow(() -> new EntityNotFoundException("Venda não encontrada."));
        FiscalDocument document = fiscalDocumentRepository.findBySaleId(id).orElse(null);
        return response(sale, document);
    }

    @Transactional(readOnly = true)
    public Page<SaleResponseDto> findAll(Pageable pageable) {
        return saleRepository.findAll(pageable)
            .map(sale -> response(
                sale,
                fiscalDocumentRepository.findBySaleId(sale.getId()).orElse(null)
            ));
    }

    @Transactional(readOnly = true)
    public Page<SaleResponseDto> findFiscalPending(Pageable pageable) {
        Set<FiscalDocumentStatus> pendingStatuses = Set.of(
            FiscalDocumentStatus.AGUARDANDO_AUTORIZACAO,
            FiscalDocumentStatus.PENDENTE_CONSULTA,
            FiscalDocumentStatus.CONTINGENCIA,
            FiscalDocumentStatus.CANCELAMENTO_PENDENTE
        );

        return saleRepository.findByFiscalDocumentStatusIn(pendingStatuses, pageable)
            .map(sale -> response(
                sale,
                fiscalDocumentRepository.findBySaleId(sale.getId())
                    .orElseThrow(() -> new EntityNotFoundException("Documento fiscal da venda não encontrado."))
            ));
    }

    @Transactional
    public SaleResponseDto consult(Long saleId) {
        Sale sale = saleRepository.findForFiscal(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Venda não encontrada."));
        FiscalDocument document = fiscalDocumentRepository.findBySaleId(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Documento fiscal não encontrado."));

        if (document.getStatus() != FiscalDocumentStatus.PENDENTE_CONSULTA) {
            throw new BusinessException("Somente uma NFC-e pendente de consulta pode ser consultada por este fluxo.");
        }

        NfceIssueResult result = gateway.consult(document.getEstablishment(), document);
        applyResult(document, result);
        sale.setStatus(saleStatusForFiscalResult(result.status()));
        return response(sale, document);
    }

    @Transactional
    public SaleResponseDto consultPendingCancellation(Long saleId) {
        Sale sale = saleRepository.findForFiscal(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Venda não encontrada."));
        FiscalDocument document = fiscalDocumentRepository.findBySaleId(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Documento fiscal não encontrado."));

        if (document.getStatus() == FiscalDocumentStatus.CANCELADA) {
            return response(sale, document);
        }
        if (document.getStatus() != FiscalDocumentStatus.CANCELAMENTO_PENDENTE) {
            throw new BusinessException("Somente um cancelamento pendente pode ser consultado por este fluxo.");
        }
        if (document.getAccessKey() == null || document.getAccessKey().length() != 44) {
            throw new BusinessException("A NFC-e não possui chave de acesso válida para consultar o cancelamento.");
        }

        NfceIssueResult result = gateway.consultCancellation(document.getEstablishment(), document);
        document.setResponseXml(result.responseXml());
        document.setReason(result.reason());

        if (result.status() == FiscalDocumentStatus.CANCELADA) {
            document.setStatus(FiscalDocumentStatus.CANCELADA);
            document.setCancellationProtocol(result.protocol());
            document.setCanceledAt(Instant.now());
            sale.setStatus(SaleStatus.CANCELADA);
            stockService.registerSaleCancellationReturn(sale);
            updatePendingCancellationEvent(document, result);
        } else {
            document.setStatus(FiscalDocumentStatus.CANCELAMENTO_PENDENTE);
            sale.setStatus(SaleStatus.FISCAL_PENDENTE);
        }

        return response(sale, document);
    }

    @Transactional
    public SaleResponseDto cancel(Long saleId, String justification) {
        if (justification == null || justification.trim().length() < 15) {
            throw new BusinessException("A justificativa do cancelamento deve possuir pelo menos 15 caracteres.");
        }

        Sale sale = saleRepository.findForFiscal(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Venda não encontrada."));
        FiscalDocument document = fiscalDocumentRepository.findBySaleId(saleId)
            .orElseThrow(() -> new EntityNotFoundException("Documento fiscal não encontrado."));

        if (document.getStatus() != FiscalDocumentStatus.AUTORIZADA) {
            throw new BusinessException("Somente uma NFC-e autorizada pode ser cancelada.");
        }
        if (document.getAccessKey() == null || document.getProtocol() == null) {
            throw new BusinessException("A NFC-e não possui chave de acesso e protocolo necessários ao cancelamento.");
        }

        NfceIssueResult result = gateway.cancel(document.getEstablishment(), document, justification.trim());
        document.setResponseXml(result.responseXml());
        document.setReason(result.reason());
        document.setCancellationProtocol(result.protocol());
        document.setStatus(result.status());

        saveCancellationEvent(document, result, justification.trim());

        if (result.status() == FiscalDocumentStatus.CANCELADA) {
            document.setCanceledAt(Instant.now());
            sale.setStatus(SaleStatus.CANCELADA);
            stockService.registerSaleCancellationReturn(sale);
        }

        return response(sale, document);
    }

    private void updatePendingCancellationEvent(FiscalDocument document, NfceIssueResult result) {
        fiscalEventRepository.findTopByDocumentIdAndEventTypeOrderBySequenceNumberDesc(
            document.getId(), FiscalEventType.CANCELAMENTO
        ).ifPresent(event -> {
            if (event.getStatus() == FiscalDocumentStatus.CANCELAMENTO_PENDENTE) {
                event.setStatus(FiscalDocumentStatus.CANCELADA);
                event.setResponseXml(result.responseXml());
                event.setProtocol(result.protocol());
                event.setReason(result.reason());
                fiscalEventRepository.save(event);
            }
        });
    }

    private void saveCancellationEvent(FiscalDocument document, NfceIssueResult result, String justification) {
        FiscalEvent event = new FiscalEvent();
        event.setDocument(document);
        event.setEventType(FiscalEventType.CANCELAMENTO);
        event.setSequenceNumber(fiscalEventRepository.findMaxSequenceNumber(document.getId(), FiscalEventType.CANCELAMENTO) + 1);
        event.setStatus(result.status());
        event.setXml(result.signedXml());
        event.setResponseXml(result.responseXml());
        event.setProtocol(result.protocol());
        event.setReason(result.reason() == null ? justification : result.reason());
        event.setOccurredAt(Instant.now());
        fiscalEventRepository.save(event);
    }

    private void applyResult(FiscalDocument document, NfceIssueResult result) {
        document.setStatus(result.status());
        if (result.accessKey() != null) {
            document.setAccessKey(result.accessKey());
        }
        if (result.signedXml() != null) {
            document.setXml(result.signedXml());
        }
        document.setResponseXml(result.responseXml());
        document.setProtocol(result.protocol());
        document.setReceiptNumber(result.receiptNumber());
        document.setReason(result.reason());
        if (result.issuedAt() != null) {
            document.setIssuedAt(result.issuedAt());
        }
    }

    private SaleStatus saleStatusForFiscalResult(FiscalDocumentStatus status) {
        return switch (status) {
            case AUTORIZADA -> SaleStatus.FISCALIZADA;
            case REJEITADA -> SaleStatus.FISCAL_REJEITADA;
            case PENDENTE_CONSULTA, AGUARDANDO_AUTORIZACAO, CANCELAMENTO_PENDENTE, CONTINGENCIA -> SaleStatus.FISCAL_PENDENTE;
            case CANCELADA -> SaleStatus.CANCELADA;
        };
    }

    private void validateProductFiscalData(Product product, FiscalProductProfile profile) {
        if (product.getNcm() == null || !product.getNcm().matches("\\d{8}")) {
            throw new FiscalConfigurationException("O produto " + product.getCode() + " precisa ter NCM com 8 dígitos.");
        }
        if (product.getOrigin() == null || !product.getOrigin().matches("[0-8]")) {
            throw new FiscalConfigurationException("O produto " + product.getCode() + " precisa ter origem fiscal válida.");
        }
        if (product.getGtin() != null && !product.getGtin().isBlank()
            && !product.getGtin().matches("\\d{8}|\\d{12}|\\d{13}|\\d{14}")) {
            throw new FiscalConfigurationException("O GTIN do produto " + product.getCode() + " é inválido para emissão fiscal.");
        }
        if (profile.getCfop() == null || !profile.getCfop().matches("\\d{4}")
            || profile.getIcmsCstCsosn() == null || !profile.getIcmsCstCsosn().matches("\\d{2,3}")
            || profile.getPisCst() == null || !profile.getPisCst().matches("\\d{2}")
            || profile.getCofinsCst() == null || !profile.getCofinsCst().matches("\\d{2}")) {
            throw new FiscalConfigurationException("O perfil fiscal do produto " + product.getCode() + " está incompleto ou possui códigos inválidos.");
        }

        if (profile.getIbsRate() != null || profile.getCbsRate() != null) {
            throw new FiscalConfigurationException(
                "O perfil do produto ainda utiliza as alíquotas IBS/CBS legadas. Remova ibsRate/cbsRate; as alíquotas RTC são resolvidas por vigência."
            );
        }

        if (List.of("01", "02").contains(profile.getPisCst()) && profile.getPisRate() == null) {
            throw new FiscalConfigurationException("Informe a alíquota de PIS para o CST " + profile.getPisCst() + ".");
        }
        if (List.of("01", "02").contains(profile.getCofinsCst()) && profile.getCofinsRate() == null) {
            throw new FiscalConfigurationException("Informe a alíquota de COFINS para o CST " + profile.getCofinsCst() + ".");
        }
        if (!List.of("01", "02", "04", "05", "06", "07", "08", "09").contains(profile.getPisCst())) {
            throw new FiscalConfigurationException("CST de PIS " + profile.getPisCst() + " ainda não possui regra fiscal parametrizada.");
        }
        if (!List.of("01", "02", "04", "05", "06", "07", "08", "09").contains(profile.getCofinsCst())) {
            throw new FiscalConfigurationException("CST de COFINS " + profile.getCofinsCst() + " ainda não possui regra fiscal parametrizada.");
        }

        if (!List.of("102", "103", "300", "400", "00", "40", "41").contains(profile.getIcmsCstCsosn())) {
            throw new FiscalConfigurationException("CST/CSOSN " + profile.getIcmsCstCsosn() + " ainda não possui regra fiscal parametrizada.");
        }
    }

    private void validatePayment(SalePaymentRequestDto request, BigDecimal amount) {
        if (amount.signum() <= 0) {
            throw new BusinessException("O valor de cada pagamento deve ser maior que zero.");
        }
        if ((request.paymentMethod() == PaymentMethod.CREDITO || request.paymentMethod() == PaymentMethod.DEBITO)
            && request.cardBrand() != null && !request.cardBrand().isBlank()
            && !request.cardBrand().matches("\\d{2}")) {
            throw new BusinessException("A bandeira do cartão deve usar o código fiscal de dois dígitos quando informada.");
        }
    }

    private void allocateSaleDiscount(List<SaleItem> items, BigDecimal saleDiscount, BigDecimal itemTotalBase) {
        if (saleDiscount.signum() == 0 || itemTotalBase.signum() == 0) {
            return;
        }

        BigDecimal remaining = saleDiscount;
        for (int index = 0; index < items.size(); index++) {
            SaleItem item = items.get(index);
            BigDecimal allocation;
            if (index == items.size() - 1) {
                allocation = remaining;
            } else if (item.getTotal().signum() == 0) {
                allocation = BigDecimal.ZERO.setScale(4);
            } else {
                allocation = item.getTotal()
                    .multiply(saleDiscount)
                    .divide(itemTotalBase, 4, RoundingMode.HALF_UP);
            }
            if (allocation.compareTo(item.getTotal()) > 0) {
                allocation = item.getTotal();
            }
            item.setDiscount(item.getDiscount().add(allocation).setScale(4, RoundingMode.HALF_UP));
            item.setTotal(item.getTotal().subtract(allocation).setScale(4, RoundingMode.HALF_UP));
            remaining = remaining.subtract(allocation).setScale(4, RoundingMode.HALF_UP);
        }
    }

    private SaleResponseDto response(Sale sale, FiscalDocument document) {
        return new SaleResponseDto(
            sale.getId(),
            sale.getFiscalEstablishment().getId(),
            sale.getClient() == null ? null : sale.getClient().getId(),
            sale.getUser().getId(),
            sale.getStatus(),
            sale.getSaleAt(),
            sale.getSubtotal(),
            sale.getDiscount(),
            sale.getTotal(),
            sale.getPayments().stream().map(SalePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add),
            sale.getPayments().stream().map(SalePayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .subtract(sale.getTotal()).max(BigDecimal.ZERO),
            sale.getItems().stream().map(net.ddns.lexdev.systempro_api.dto.SaleItemResponseDto::fromEntity).toList(),
            sale.getPayments().stream().map(net.ddns.lexdev.systempro_api.dto.SalePaymentResponseDto::fromEntity).toList(),
            document == null ? null : FiscalDocumentResponseDto.fromEntity(document),
            sale.getNote()
        );
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(4) : value.setScale(4, RoundingMode.HALF_UP);
    }

    private static String normalizeDocument(String value) {
        String normalized = CpfCnpjNormalizer.normalize(value);
        return normalized == null || normalized.isBlank() ? null : normalized;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}