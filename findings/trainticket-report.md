# Plimsoll analysis of `train-ticket`

**34 error, 0 warn, 78 info**

## Amplification per service

Limit: 10

| Service | Amplification |
| --- | --- |
| ts-admin-basic-info-service | ×3 |
| ts-admin-order-service | ×3 |
| ts-admin-route-service | ×3 |
| ts-admin-travel-service | ×3 |
| ts-admin-user-service | ×3 |
| ts-assurance-service | ×3 |
| ts-auth-service | ×6 |
| ts-avatar-service | ×3 |
| ts-basic-service | ×3 |
| ts-cancel-service | ×3 |
| ts-config-service | ×3 |
| ts-consign-price-service | ×3 |
| ts-consign-service | ×3 |
| ts-contacts-service | ×3 |
| ts-execute-service | ×3 |
| ts-food-service | ×3 |
| ts-gateway-service | ×1 |
| ts-inside-payment-service | ×3 |
| ts-notification-service | ×3 |
| ts-order-other-service | ×3 |
| ts-order-service | ×3 |
| ts-payment-service | ×3 |
| ts-preserve-other-service | ×3 |
| ts-preserve-service | ×3 |
| ts-price-service | ×3 |
| ts-rebook-service | ×3 |
| ts-route-plan-service | ×3 |
| ts-route-service | ×3 |
| ts-seat-service | ×3 |
| ts-security-service | ×3 |
| ts-station-food-service | ×3 |
| ts-station-service | ×3 |
| ts-train-food-service | ×3 |
| ts-train-service | ×3 |
| ts-travel-plan-service | ×3 |
| ts-travel-service | ×3 |
| ts-travel2-service | ×3 |
| ts-user-service | ×3 |
| ts-verification-code-service | ×3 |

## Findings

| Severity | Rule | Location | Detail |
| --- | --- | --- | --- |
| ERROR | PLM-A3 | ts-gateway-service→ts-admin-basic-info-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-admin-order-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-admin-route-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-admin-travel-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-admin-user-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-assurance-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-auth-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-auth-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-basic-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-config-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-consign-price-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-consign-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-contacts-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-food-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-inside-payment-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-notification-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-order-other-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-order-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-payment-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-preserve-other-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-preserve-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-price-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-rebook-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-route-plan-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-route-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-seat-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-security-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-station-food-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-station-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-train-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-travel-plan-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-travel-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-travel2-service | non-idempotent call retried, 3 tries |
| ERROR | PLM-A3 | ts-gateway-service→ts-user-service | non-idempotent call retried, 3 tries |
| INFO | PLM-A2a | ts-gateway-service→ts-admin-basic-info-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-admin-basic-info-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-admin-order-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-admin-order-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-admin-route-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-admin-route-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-admin-travel-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-admin-travel-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-admin-user-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-admin-user-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-assurance-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-assurance-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-auth-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-auth-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-auth-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-auth-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-avatar-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-avatar-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-basic-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-basic-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-cancel-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-cancel-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-config-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-config-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-consign-price-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-consign-price-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-consign-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-consign-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-contacts-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-contacts-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-execute-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-execute-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-food-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-food-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-inside-payment-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-inside-payment-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-notification-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-notification-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-order-other-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-order-other-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-order-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-order-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-payment-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-payment-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-preserve-other-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-preserve-other-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-preserve-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-preserve-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-price-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-price-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-rebook-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-rebook-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-route-plan-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-route-plan-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-route-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-route-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-seat-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-seat-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-security-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-security-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-station-food-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-station-food-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-station-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-station-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-train-food-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-train-food-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-train-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-train-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-travel-plan-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-travel-plan-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-travel-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-travel-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-travel2-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-travel2-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-user-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-user-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2a | ts-gateway-service→ts-verification-code-service | layer 2 (istio): cannot evaluate: ts-gateway-service→ts-verification-code-service layer 2 (istio) perTryTimeout, ts-gateway-service deadline UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-admin-basic-info-service | cannot evaluate: ts-admin-basic-info-service deadline, ts-gateway-service→ts-admin-basic-info-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-admin-basic-info-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-admin-order-service | cannot evaluate: ts-admin-order-service deadline, ts-gateway-service→ts-admin-order-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-admin-order-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-admin-route-service | cannot evaluate: ts-admin-route-service deadline, ts-gateway-service→ts-admin-route-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-admin-route-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-admin-travel-service | cannot evaluate: ts-admin-travel-service deadline, ts-gateway-service→ts-admin-travel-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-admin-travel-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-admin-user-service | cannot evaluate: ts-admin-user-service deadline, ts-gateway-service→ts-admin-user-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-admin-user-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-assurance-service | cannot evaluate: ts-assurance-service deadline, ts-gateway-service→ts-assurance-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-assurance-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-auth-service | cannot evaluate: ts-auth-service deadline, ts-gateway-service→ts-auth-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-auth-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-auth-service | cannot evaluate: ts-auth-service deadline, ts-gateway-service→ts-auth-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-auth-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-avatar-service | cannot evaluate: ts-avatar-service deadline, ts-gateway-service→ts-avatar-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-avatar-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-basic-service | cannot evaluate: ts-basic-service deadline, ts-gateway-service→ts-basic-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-basic-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-cancel-service | cannot evaluate: ts-cancel-service deadline, ts-gateway-service→ts-cancel-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-cancel-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-config-service | cannot evaluate: ts-config-service deadline, ts-gateway-service→ts-config-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-config-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-consign-price-service | cannot evaluate: ts-consign-price-service deadline, ts-gateway-service→ts-consign-price-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-consign-price-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-consign-service | cannot evaluate: ts-consign-service deadline, ts-gateway-service→ts-consign-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-consign-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-contacts-service | cannot evaluate: ts-contacts-service deadline, ts-gateway-service→ts-contacts-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-contacts-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-execute-service | cannot evaluate: ts-execute-service deadline, ts-gateway-service→ts-execute-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-execute-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-food-service | cannot evaluate: ts-food-service deadline, ts-gateway-service→ts-food-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-food-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-inside-payment-service | cannot evaluate: ts-inside-payment-service deadline, ts-gateway-service→ts-inside-payment-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-inside-payment-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-notification-service | cannot evaluate: ts-notification-service deadline, ts-gateway-service→ts-notification-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-notification-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-order-other-service | cannot evaluate: ts-order-other-service deadline, ts-gateway-service→ts-order-other-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-order-other-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-order-service | cannot evaluate: ts-order-service deadline, ts-gateway-service→ts-order-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-order-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-payment-service | cannot evaluate: ts-payment-service deadline, ts-gateway-service→ts-payment-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-payment-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-preserve-other-service | cannot evaluate: ts-preserve-other-service deadline, ts-gateway-service→ts-preserve-other-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-preserve-other-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-preserve-service | cannot evaluate: ts-preserve-service deadline, ts-gateway-service→ts-preserve-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-preserve-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-price-service | cannot evaluate: ts-price-service deadline, ts-gateway-service→ts-price-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-price-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-rebook-service | cannot evaluate: ts-rebook-service deadline, ts-gateway-service→ts-rebook-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-rebook-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-route-plan-service | cannot evaluate: ts-route-plan-service deadline, ts-gateway-service→ts-route-plan-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-route-plan-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-route-service | cannot evaluate: ts-route-service deadline, ts-gateway-service→ts-route-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-route-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-seat-service | cannot evaluate: ts-seat-service deadline, ts-gateway-service→ts-seat-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-seat-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-security-service | cannot evaluate: ts-security-service deadline, ts-gateway-service→ts-security-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-security-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-station-food-service | cannot evaluate: ts-station-food-service deadline, ts-gateway-service→ts-station-food-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-station-food-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-station-service | cannot evaluate: ts-station-service deadline, ts-gateway-service→ts-station-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-station-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-train-food-service | cannot evaluate: ts-train-food-service deadline, ts-gateway-service→ts-train-food-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-train-food-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-train-service | cannot evaluate: ts-train-service deadline, ts-gateway-service→ts-train-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-train-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-travel-plan-service | cannot evaluate: ts-travel-plan-service deadline, ts-gateway-service→ts-travel-plan-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-travel-plan-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-travel-service | cannot evaluate: ts-travel-service deadline, ts-gateway-service→ts-travel-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-travel-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-travel2-service | cannot evaluate: ts-travel2-service deadline, ts-gateway-service→ts-travel2-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-travel2-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-user-service | cannot evaluate: ts-user-service deadline, ts-gateway-service→ts-user-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-user-service layer 2 (istio) perTryTimeout UNKNOWN |
| INFO | PLM-A2b | ts-gateway-service→ts-verification-code-service | cannot evaluate: ts-verification-code-service deadline, ts-gateway-service→ts-verification-code-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-verification-code-service layer 2 (istio) perTryTimeout UNKNOWN |

## Values used

### ERROR PLM-A3 ts-gateway-service→ts-admin-basic-info-service

- ts-gateway-service→ts-admin-basic-info-service idempotent = false [LIBRARY_DEFAULT: POST (ts-admin-basic-info-service /api/v1/adminbasicservice/adminbasic/contacts, ts-admin-basic-info-service/src/main/java/adminbasic/controller/AdminBasicInfoController.java:56); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-admin-basic-info-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route admin-basic-info (ts-gateway-service/src/main/resources/application.yml:16) or in default-filters]
- ts-gateway-service→ts-admin-basic-info-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-admin-order-service

- ts-gateway-service→ts-admin-order-service idempotent = false [LIBRARY_DEFAULT: POST (ts-admin-order-service /api/v1/adminorderservice/adminorder, ts-admin-order-service/src/main/java/adminorder/controller/AdminOrderController.java:38); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-admin-order-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route admin-order (ts-gateway-service/src/main/resources/application.yml:21) or in default-filters]
- ts-gateway-service→ts-admin-order-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-admin-route-service

- ts-gateway-service→ts-admin-route-service idempotent = false [LIBRARY_DEFAULT: POST (ts-admin-route-service /api/v1/adminrouteservice/adminroute, ts-admin-route-service/src/main/java/adminroute/controller/AdminRouteController.java:38); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-admin-route-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route admin-route (ts-gateway-service/src/main/resources/application.yml:26) or in default-filters]
- ts-gateway-service→ts-admin-route-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-admin-travel-service

- ts-gateway-service→ts-admin-travel-service idempotent = false [LIBRARY_DEFAULT: POST (ts-admin-travel-service /api/v1/admintravelservice/admintravel, ts-admin-travel-service/src/main/java/admintravel/controller/AdminTravelController.java:37); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-admin-travel-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route admin-travel (ts-gateway-service/src/main/resources/application.yml:31) or in default-filters]
- ts-gateway-service→ts-admin-travel-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-admin-user-service

- ts-gateway-service→ts-admin-user-service idempotent = false [LIBRARY_DEFAULT: POST (ts-admin-user-service /api/v1/adminuserservice/users, ts-admin-user-service/src/main/java/adminuser/controller/AdminUserController.java:44); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-admin-user-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route admin-user (ts-gateway-service/src/main/resources/application.yml:36) or in default-filters]
- ts-gateway-service→ts-admin-user-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-assurance-service

- ts-gateway-service→ts-assurance-service idempotent = false [LIBRARY_DEFAULT: PATCH (ts-assurance-service /api/v1/assuranceservice/assurances/{assuranceId}/{orderId}/{typeIndex}, ts-assurance-service/src/main/java/assurance/controller/AssuranceController.java:61); http RFC 5789 default (verified by doc), https://www.rfc-editor.org/rfc/rfc5789#section-2]
- ts-gateway-service→ts-assurance-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route assurance (ts-gateway-service/src/main/resources/application.yml:41) or in default-filters]
- ts-gateway-service→ts-assurance-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-auth-service

- ts-gateway-service→ts-auth-service idempotent = false [LIBRARY_DEFAULT: POST (ts-auth-service /api/v1/auth, ts-auth-service/src/main/java/auth/controller/AuthController.java:36); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-auth-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route auth (ts-gateway-service/src/main/resources/application.yml:46) or in default-filters]
- ts-gateway-service→ts-auth-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-auth-service

- ts-gateway-service→ts-auth-service idempotent = false [LIBRARY_DEFAULT: POST (ts-auth-service /api/v1/users/login, ts-auth-service/src/main/java/auth/controller/UserController.java:41); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-auth-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route auth-user (ts-gateway-service/src/main/resources/application.yml:51) or in default-filters]
- ts-gateway-service→ts-auth-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-basic-service

- ts-gateway-service→ts-basic-service idempotent = false [LIBRARY_DEFAULT: POST (ts-basic-service /api/v1/basicservice/basic/travel, ts-basic-service/src/main/java/fdse/microservice/controller/BasicController.java:35); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-basic-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route basic (ts-gateway-service/src/main/resources/application.yml:61) or in default-filters]
- ts-gateway-service→ts-basic-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-config-service

- ts-gateway-service→ts-config-service idempotent = false [LIBRARY_DEFAULT: POST (ts-config-service /api/v1/configservice/configs, ts-config-service/src/main/java/config/controller/ConfigController.java:43); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-config-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route config (ts-gateway-service/src/main/resources/application.yml:71) or in default-filters]
- ts-gateway-service→ts-config-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-consign-price-service

- ts-gateway-service→ts-consign-price-service idempotent = false [LIBRARY_DEFAULT: POST (ts-consign-price-service /api/v1/consignpriceservice/consignprice, ts-consign-price-service/src/main/java/consignprice/controller/ConsignPriceController.java:51); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-consign-price-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route consign-price (ts-gateway-service/src/main/resources/application.yml:76) or in default-filters]
- ts-gateway-service→ts-consign-price-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-consign-service

- ts-gateway-service→ts-consign-service idempotent = false [LIBRARY_DEFAULT: POST (ts-consign-service /api/v1/consignservice/consigns, ts-consign-service/src/main/java/consign/controller/ConsignController.java:33); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-consign-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route consign (ts-gateway-service/src/main/resources/application.yml:81) or in default-filters]
- ts-gateway-service→ts-consign-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-contacts-service

- ts-gateway-service→ts-contacts-service idempotent = false [LIBRARY_DEFAULT: POST (ts-contacts-service /api/v1/contactservice/contacts, ts-contacts-service/src/main/java/contacts/controller/ContactsController.java:42); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-contacts-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route contacts (ts-gateway-service/src/main/resources/application.yml:86) or in default-filters]
- ts-gateway-service→ts-contacts-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-food-service

- ts-gateway-service→ts-food-service idempotent = false [LIBRARY_DEFAULT: POST (ts-food-service /api/v1/foodservice/orders, ts-food-service/src/main/java/foodsearch/controller/FoodController.java:56); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-food-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route food (ts-gateway-service/src/main/resources/application.yml:96) or in default-filters]
- ts-gateway-service→ts-food-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-inside-payment-service

- ts-gateway-service→ts-inside-payment-service idempotent = false [LIBRARY_DEFAULT: POST (ts-inside-payment-service /api/v1/inside_pay_service/inside_payment, ts-inside-payment-service/src/main/java/inside_payment/controller/InsidePaymentController.java:31); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-inside-payment-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route inside-payment (ts-gateway-service/src/main/resources/application.yml:101) or in default-filters]
- ts-gateway-service→ts-inside-payment-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-notification-service

- ts-gateway-service→ts-notification-service idempotent = false [LIBRARY_DEFAULT: POST (ts-notification-service /api/v1/notifyservice/notification/preserve_success, ts-notification-service/src/main/java/notification/controller/NotificationController.java:57); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-notification-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route notification (ts-gateway-service/src/main/resources/application.yml:106) or in default-filters]
- ts-gateway-service→ts-notification-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-order-other-service

- ts-gateway-service→ts-order-other-service idempotent = false [LIBRARY_DEFAULT: POST (ts-order-other-service /api/v1/orderOtherService/orderOther/tickets, ts-order-other-service/src/main/java/other/controller/OrderOtherController.java:40); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-order-other-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route order-other (ts-gateway-service/src/main/resources/application.yml:111) or in default-filters]
- ts-gateway-service→ts-order-other-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-order-service

- ts-gateway-service→ts-order-service idempotent = false [LIBRARY_DEFAULT: POST (ts-order-service /api/v1/orderservice/order/tickets, ts-order-service/src/main/java/order/controller/OrderController.java:37); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-order-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route order (ts-gateway-service/src/main/resources/application.yml:116) or in default-filters]
- ts-gateway-service→ts-order-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-payment-service

- ts-gateway-service→ts-payment-service idempotent = false [LIBRARY_DEFAULT: POST (ts-payment-service /api/v1/paymentservice/payment, ts-payment-service/src/main/java/com/trainticket/controller/PaymentController.java:32); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-payment-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route payment (ts-gateway-service/src/main/resources/application.yml:121) or in default-filters]
- ts-gateway-service→ts-payment-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-preserve-other-service

- ts-gateway-service→ts-preserve-other-service idempotent = false [LIBRARY_DEFAULT: POST (ts-preserve-other-service /api/v1/preserveotherservice/preserveOther, ts-preserve-other-service/src/main/java/preserveOther/controller/PreserveOtherController.java:32); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-preserve-other-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route preserve-other (ts-gateway-service/src/main/resources/application.yml:126) or in default-filters]
- ts-gateway-service→ts-preserve-other-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-preserve-service

- ts-gateway-service→ts-preserve-service idempotent = false [LIBRARY_DEFAULT: POST (ts-preserve-service /api/v1/preserveservice/preserve, ts-preserve-service/src/main/java/preserve/controller/PreserveController.java:32); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-preserve-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route preserve (ts-gateway-service/src/main/resources/application.yml:131) or in default-filters]
- ts-gateway-service→ts-preserve-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-price-service

- ts-gateway-service→ts-price-service idempotent = false [LIBRARY_DEFAULT: POST (ts-price-service /api/v1/priceservice/prices/byRouteIdsAndTrainTypes, ts-price-service/src/main/java/price/controller/PriceController.java:43); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-price-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route price (ts-gateway-service/src/main/resources/application.yml:136) or in default-filters]
- ts-gateway-service→ts-price-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-rebook-service

- ts-gateway-service→ts-rebook-service idempotent = false [LIBRARY_DEFAULT: POST (ts-rebook-service /api/v1/rebookservice/rebook/difference, ts-rebook-service/src/main/java/rebook/controller/RebookController.java:31); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-rebook-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route rebook (ts-gateway-service/src/main/resources/application.yml:141) or in default-filters]
- ts-gateway-service→ts-rebook-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-route-plan-service

- ts-gateway-service→ts-route-plan-service idempotent = false [LIBRARY_DEFAULT: POST (ts-route-plan-service /api/v1/routeplanservice/routePlan/cheapestRoute, ts-route-plan-service/src/main/java/plan/controller/RoutePlanController.java:30); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-route-plan-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route route-plan (ts-gateway-service/src/main/resources/application.yml:146) or in default-filters]
- ts-gateway-service→ts-route-plan-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-route-service

- ts-gateway-service→ts-route-service idempotent = false [LIBRARY_DEFAULT: POST (ts-route-service /api/v1/routeservice/routes, ts-route-service/src/main/java/route/controller/RouteController.java:33); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-route-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route route (ts-gateway-service/src/main/resources/application.yml:151) or in default-filters]
- ts-gateway-service→ts-route-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-seat-service

- ts-gateway-service→ts-seat-service idempotent = false [LIBRARY_DEFAULT: POST (ts-seat-service /api/v1/seatservice/seats, ts-seat-service/src/main/java/seat/controller/SeatController.java:39); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-seat-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route seat (ts-gateway-service/src/main/resources/application.yml:156) or in default-filters]
- ts-gateway-service→ts-seat-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-security-service

- ts-gateway-service→ts-security-service idempotent = false [LIBRARY_DEFAULT: POST (ts-security-service /api/v1/securityservice/securityConfigs, ts-security-service/src/main/java/security/controller/SecurityController.java:39); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-security-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route security (ts-gateway-service/src/main/resources/application.yml:161) or in default-filters]
- ts-gateway-service→ts-security-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-station-food-service

- ts-gateway-service→ts-station-food-service idempotent = false [LIBRARY_DEFAULT: POST (ts-station-food-service /api/v1/stationfoodservice/stationfoodstores, ts-station-food-service/src/main/java/food/controller/StationFoodController.java:44); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-station-food-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route station-food (ts-gateway-service/src/main/resources/application.yml:166) or in default-filters]
- ts-gateway-service→ts-station-food-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-station-service

- ts-gateway-service→ts-station-service idempotent = false [LIBRARY_DEFAULT: POST (ts-station-service /api/v1/stationservice/stations, ts-station-service/src/main/java/fdse/microservice/controller/StationController.java:38); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-station-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route station (ts-gateway-service/src/main/resources/application.yml:171) or in default-filters]
- ts-gateway-service→ts-station-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-train-service

- ts-gateway-service→ts-train-service idempotent = false [LIBRARY_DEFAULT: POST (ts-train-service /api/v1/trainservice/trains, ts-train-service/src/main/java/train/controller/TrainController.java:34); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-train-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route train (ts-gateway-service/src/main/resources/application.yml:181) or in default-filters]
- ts-gateway-service→ts-train-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-travel-plan-service

- ts-gateway-service→ts-travel-plan-service idempotent = false [LIBRARY_DEFAULT: POST (ts-travel-plan-service /api/v1/travelplanservice/travelPlan/transferResult, ts-travel-plan-service/src/main/java/travelplan/controller/TravelPlanController.java:32); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-travel-plan-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route travel-plan (ts-gateway-service/src/main/resources/application.yml:191) or in default-filters]
- ts-gateway-service→ts-travel-plan-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-travel-service

- ts-gateway-service→ts-travel-service idempotent = false [LIBRARY_DEFAULT: POST (ts-travel-service /api/v1/travelservice/trips/routes, ts-travel-service/src/main/java/travel/controller/TravelController.java:58); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-travel-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route travel (ts-gateway-service/src/main/resources/application.yml:196) or in default-filters]
- ts-gateway-service→ts-travel-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-travel2-service

- ts-gateway-service→ts-travel2-service idempotent = false [LIBRARY_DEFAULT: POST (ts-travel2-service /api/v1/travel2service/trips/routes, ts-travel2-service/src/main/java/travel2/controller/Travel2Controller.java:51); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-travel2-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route travel2 (ts-gateway-service/src/main/resources/application.yml:186) or in default-filters]
- ts-gateway-service→ts-travel2-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### ERROR PLM-A3 ts-gateway-service→ts-user-service

- ts-gateway-service→ts-user-service idempotent = false [LIBRARY_DEFAULT: POST (ts-user-service /api/v1/userservice/users/register, ts-user-service/src/main/java/user/controller/UserController.java:52); http RFC 9110 default (verified by doc), https://www.rfc-editor.org/rfc/rfc9110#section-9.2.2]
- ts-gateway-service→ts-user-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route user (ts-gateway-service/src/main/resources/application.yml:201) or in default-filters]
- ts-gateway-service→ts-user-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]

### INFO PLM-A2a ts-gateway-service→ts-admin-basic-info-service

- ts-gateway-service→ts-admin-basic-info-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-admin-basic-info-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-basic-info-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-basic-info-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-admin-basic-info-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-admin-basic-info-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-admin-order-service

- ts-gateway-service→ts-admin-order-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-admin-order-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-order-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-order-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-admin-order-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-admin-order-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-admin-route-service

- ts-gateway-service→ts-admin-route-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-admin-route-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-route-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-route-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-admin-route-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-admin-route-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-admin-travel-service

- ts-gateway-service→ts-admin-travel-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-admin-travel-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-travel-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-travel-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-admin-travel-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-admin-travel-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-admin-user-service

- ts-gateway-service→ts-admin-user-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-admin-user-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-user-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-user-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-admin-user-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-admin-user-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-assurance-service

- ts-gateway-service→ts-assurance-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-assurance-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-assurance-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-assurance-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-assurance-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-assurance-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-auth-service

- ts-gateway-service→ts-auth-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-auth-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-auth-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-auth-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-auth-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-auth-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-auth-service

- ts-gateway-service→ts-auth-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-auth-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-auth-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-auth-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-auth-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-auth-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-avatar-service

- ts-gateway-service→ts-avatar-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-avatar-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-avatar-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-avatar-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-avatar-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-avatar-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-basic-service

- ts-gateway-service→ts-basic-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-basic-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-basic-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-basic-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-basic-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-basic-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-cancel-service

- ts-gateway-service→ts-cancel-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-cancel-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-cancel-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-cancel-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-cancel-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-cancel-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-config-service

- ts-gateway-service→ts-config-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-config-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-config-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-config-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-config-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-config-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-consign-price-service

- ts-gateway-service→ts-consign-price-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-consign-price-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-consign-price-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-consign-price-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-consign-price-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-consign-price-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-consign-service

- ts-gateway-service→ts-consign-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-consign-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-consign-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-consign-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-consign-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-consign-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-contacts-service

- ts-gateway-service→ts-contacts-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-contacts-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-contacts-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-contacts-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-contacts-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-contacts-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-execute-service

- ts-gateway-service→ts-execute-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-execute-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-execute-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-execute-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-execute-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-execute-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-food-service

- ts-gateway-service→ts-food-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-food-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-food-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-food-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-food-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-food-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-inside-payment-service

- ts-gateway-service→ts-inside-payment-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-inside-payment-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-inside-payment-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-inside-payment-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-inside-payment-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-inside-payment-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-notification-service

- ts-gateway-service→ts-notification-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-notification-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-notification-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-notification-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-notification-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-notification-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-order-other-service

- ts-gateway-service→ts-order-other-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-order-other-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-order-other-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-order-other-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-order-other-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-order-other-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-order-service

- ts-gateway-service→ts-order-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-order-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-order-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-order-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-order-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-order-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-payment-service

- ts-gateway-service→ts-payment-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-payment-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-payment-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-payment-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-payment-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-payment-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-preserve-other-service

- ts-gateway-service→ts-preserve-other-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-preserve-other-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-preserve-other-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-preserve-other-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-preserve-other-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-preserve-other-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-preserve-service

- ts-gateway-service→ts-preserve-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-preserve-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-preserve-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-preserve-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-preserve-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-preserve-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-price-service

- ts-gateway-service→ts-price-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-price-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-price-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-price-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-price-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-price-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-rebook-service

- ts-gateway-service→ts-rebook-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-rebook-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-rebook-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-rebook-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-rebook-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-rebook-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-route-plan-service

- ts-gateway-service→ts-route-plan-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-route-plan-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-route-plan-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-route-plan-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-route-plan-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-route-plan-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-route-service

- ts-gateway-service→ts-route-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-route-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-route-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-route-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-route-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-route-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-seat-service

- ts-gateway-service→ts-seat-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-seat-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-seat-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-seat-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-seat-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-seat-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-security-service

- ts-gateway-service→ts-security-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-security-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-security-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-security-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-security-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-security-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-station-food-service

- ts-gateway-service→ts-station-food-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-station-food-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-station-food-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-station-food-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-station-food-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-station-food-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-station-service

- ts-gateway-service→ts-station-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-station-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-station-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-station-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-station-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-station-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-train-food-service

- ts-gateway-service→ts-train-food-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-train-food-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-train-food-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-train-food-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-train-food-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-train-food-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-train-service

- ts-gateway-service→ts-train-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-train-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-train-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-train-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-train-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-train-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-travel-plan-service

- ts-gateway-service→ts-travel-plan-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-travel-plan-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-travel-plan-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-travel-plan-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-travel-plan-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-travel-plan-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-travel-service

- ts-gateway-service→ts-travel-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-travel-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-travel-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-travel-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-travel-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-travel-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-travel2-service

- ts-gateway-service→ts-travel2-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-travel2-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-travel2-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-travel2-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-travel2-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-travel2-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-user-service

- ts-gateway-service→ts-user-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-user-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-user-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-user-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-user-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-user-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2a ts-gateway-service→ts-verification-code-service

- ts-gateway-service→ts-verification-code-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- ts-gateway-service→ts-verification-code-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-verification-code-service layer 2 (istio) backoff = 25 ms [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-verification-code-service layer 2 (istio) multiplier = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed multiplier]
- ts-gateway-service→ts-verification-code-service layer 2 (istio) maxBackoff = UNKNOWN [UNKNOWN: Envoy back-off is fully jittered exponential; no fixed cap on the minimum wait]
- ts-gateway-service deadline = UNKNOWN [UNKNOWN: ts-gateway-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-gateway-service (ADR 0009)]
- **depends-on-unknown: ts-gateway-service deadline, ts-gateway-service→ts-verification-code-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-admin-basic-info-service

- ts-admin-basic-info-service deadline = UNKNOWN [UNKNOWN: ts-admin-basic-info-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-admin-basic-info-service (ADR 0009)]
- ts-gateway-service→ts-admin-basic-info-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-admin-basic-info-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-basic-info-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route admin-basic-info (ts-gateway-service/src/main/resources/application.yml:16) or in default-filters]
- ts-gateway-service→ts-admin-basic-info-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-admin-basic-info-service deadline, ts-gateway-service→ts-admin-basic-info-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-admin-basic-info-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-admin-order-service

- ts-admin-order-service deadline = UNKNOWN [UNKNOWN: ts-admin-order-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-admin-order-service (ADR 0009)]
- ts-gateway-service→ts-admin-order-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-admin-order-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-order-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route admin-order (ts-gateway-service/src/main/resources/application.yml:21) or in default-filters]
- ts-gateway-service→ts-admin-order-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-admin-order-service deadline, ts-gateway-service→ts-admin-order-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-admin-order-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-admin-route-service

- ts-admin-route-service deadline = UNKNOWN [UNKNOWN: ts-admin-route-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-admin-route-service (ADR 0009)]
- ts-gateway-service→ts-admin-route-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-admin-route-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-route-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route admin-route (ts-gateway-service/src/main/resources/application.yml:26) or in default-filters]
- ts-gateway-service→ts-admin-route-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-admin-route-service deadline, ts-gateway-service→ts-admin-route-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-admin-route-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-admin-travel-service

- ts-admin-travel-service deadline = UNKNOWN [UNKNOWN: ts-admin-travel-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-admin-travel-service (ADR 0009)]
- ts-gateway-service→ts-admin-travel-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-admin-travel-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-travel-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route admin-travel (ts-gateway-service/src/main/resources/application.yml:31) or in default-filters]
- ts-gateway-service→ts-admin-travel-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-admin-travel-service deadline, ts-gateway-service→ts-admin-travel-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-admin-travel-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-admin-user-service

- ts-admin-user-service deadline = UNKNOWN [UNKNOWN: ts-admin-user-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-admin-user-service (ADR 0009)]
- ts-gateway-service→ts-admin-user-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-admin-user-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-admin-user-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route admin-user (ts-gateway-service/src/main/resources/application.yml:36) or in default-filters]
- ts-gateway-service→ts-admin-user-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-admin-user-service deadline, ts-gateway-service→ts-admin-user-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-admin-user-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-assurance-service

- ts-assurance-service deadline = UNKNOWN [UNKNOWN: ts-assurance-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-assurance-service (ADR 0009)]
- ts-gateway-service→ts-assurance-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-assurance-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-assurance-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route assurance (ts-gateway-service/src/main/resources/application.yml:41) or in default-filters]
- ts-gateway-service→ts-assurance-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-assurance-service deadline, ts-gateway-service→ts-assurance-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-assurance-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-auth-service

- ts-auth-service deadline = UNKNOWN [UNKNOWN: ts-auth-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-auth-service (ADR 0009)]
- ts-gateway-service→ts-auth-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-auth-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-auth-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route auth (ts-gateway-service/src/main/resources/application.yml:46) or in default-filters]
- ts-gateway-service→ts-auth-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-auth-service deadline, ts-gateway-service→ts-auth-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-auth-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-auth-service

- ts-auth-service deadline = UNKNOWN [UNKNOWN: ts-auth-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-auth-service (ADR 0009)]
- ts-gateway-service→ts-auth-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-auth-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-auth-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route auth-user (ts-gateway-service/src/main/resources/application.yml:51) or in default-filters]
- ts-gateway-service→ts-auth-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-auth-service deadline, ts-gateway-service→ts-auth-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-auth-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-avatar-service

- ts-avatar-service deadline = UNKNOWN [UNKNOWN: ts-avatar-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-avatar-service (ADR 0009)]
- ts-gateway-service→ts-avatar-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-avatar-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-avatar-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route avatar (ts-gateway-service/src/main/resources/application.yml:56) or in default-filters]
- ts-gateway-service→ts-avatar-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-avatar-service deadline, ts-gateway-service→ts-avatar-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-avatar-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-basic-service

- ts-basic-service deadline = UNKNOWN [UNKNOWN: ts-basic-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-basic-service (ADR 0009)]
- ts-gateway-service→ts-basic-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-basic-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-basic-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route basic (ts-gateway-service/src/main/resources/application.yml:61) or in default-filters]
- ts-gateway-service→ts-basic-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-basic-service deadline, ts-gateway-service→ts-basic-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-basic-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-cancel-service

- ts-cancel-service deadline = UNKNOWN [UNKNOWN: ts-cancel-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-cancel-service (ADR 0009)]
- ts-gateway-service→ts-cancel-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-cancel-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-cancel-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route cancel (ts-gateway-service/src/main/resources/application.yml:66) or in default-filters]
- ts-gateway-service→ts-cancel-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-cancel-service deadline, ts-gateway-service→ts-cancel-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-cancel-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-config-service

- ts-config-service deadline = UNKNOWN [UNKNOWN: ts-config-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-config-service (ADR 0009)]
- ts-gateway-service→ts-config-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-config-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-config-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route config (ts-gateway-service/src/main/resources/application.yml:71) or in default-filters]
- ts-gateway-service→ts-config-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-config-service deadline, ts-gateway-service→ts-config-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-config-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-consign-price-service

- ts-consign-price-service deadline = UNKNOWN [UNKNOWN: ts-consign-price-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-consign-price-service (ADR 0009)]
- ts-gateway-service→ts-consign-price-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-consign-price-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-consign-price-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route consign-price (ts-gateway-service/src/main/resources/application.yml:76) or in default-filters]
- ts-gateway-service→ts-consign-price-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-consign-price-service deadline, ts-gateway-service→ts-consign-price-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-consign-price-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-consign-service

- ts-consign-service deadline = UNKNOWN [UNKNOWN: ts-consign-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-consign-service (ADR 0009)]
- ts-gateway-service→ts-consign-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-consign-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-consign-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route consign (ts-gateway-service/src/main/resources/application.yml:81) or in default-filters]
- ts-gateway-service→ts-consign-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-consign-service deadline, ts-gateway-service→ts-consign-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-consign-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-contacts-service

- ts-contacts-service deadline = UNKNOWN [UNKNOWN: ts-contacts-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-contacts-service (ADR 0009)]
- ts-gateway-service→ts-contacts-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-contacts-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-contacts-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route contacts (ts-gateway-service/src/main/resources/application.yml:86) or in default-filters]
- ts-gateway-service→ts-contacts-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-contacts-service deadline, ts-gateway-service→ts-contacts-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-contacts-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-execute-service

- ts-execute-service deadline = UNKNOWN [UNKNOWN: ts-execute-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-execute-service (ADR 0009)]
- ts-gateway-service→ts-execute-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-execute-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-execute-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route execute (ts-gateway-service/src/main/resources/application.yml:91) or in default-filters]
- ts-gateway-service→ts-execute-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-execute-service deadline, ts-gateway-service→ts-execute-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-execute-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-food-service

- ts-food-service deadline = UNKNOWN [UNKNOWN: ts-food-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-food-service (ADR 0009)]
- ts-gateway-service→ts-food-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-food-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-food-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route food (ts-gateway-service/src/main/resources/application.yml:96) or in default-filters]
- ts-gateway-service→ts-food-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-food-service deadline, ts-gateway-service→ts-food-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-food-service layer 2 (istio) perTryTimeout**

### INFO PLM-A2b ts-gateway-service→ts-inside-payment-service

- ts-inside-payment-service deadline = UNKNOWN [UNKNOWN: ts-inside-payment-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-inside-payment-service (ADR 0009)]
- ts-gateway-service→ts-inside-payment-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-inside-payment-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-inside-payment-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route inside-payment (ts-gateway-service/src/main/resources/application.yml:101) or in default-filters]
- ts-gateway-service→ts-inside-payment-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-inside-payment-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-inside-payment-service layer 2 (istio) perTryTimeout, ts-inside-payment-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-notification-service

- ts-notification-service deadline = UNKNOWN [UNKNOWN: ts-notification-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-notification-service (ADR 0009)]
- ts-gateway-service→ts-notification-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-notification-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-notification-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route notification (ts-gateway-service/src/main/resources/application.yml:106) or in default-filters]
- ts-gateway-service→ts-notification-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-notification-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-notification-service layer 2 (istio) perTryTimeout, ts-notification-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-order-other-service

- ts-order-other-service deadline = UNKNOWN [UNKNOWN: ts-order-other-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-order-other-service (ADR 0009)]
- ts-gateway-service→ts-order-other-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-order-other-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-order-other-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route order-other (ts-gateway-service/src/main/resources/application.yml:111) or in default-filters]
- ts-gateway-service→ts-order-other-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-order-other-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-order-other-service layer 2 (istio) perTryTimeout, ts-order-other-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-order-service

- ts-order-service deadline = UNKNOWN [UNKNOWN: ts-order-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-order-service (ADR 0009)]
- ts-gateway-service→ts-order-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-order-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-order-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route order (ts-gateway-service/src/main/resources/application.yml:116) or in default-filters]
- ts-gateway-service→ts-order-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-order-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-order-service layer 2 (istio) perTryTimeout, ts-order-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-payment-service

- ts-payment-service deadline = UNKNOWN [UNKNOWN: ts-payment-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-payment-service (ADR 0009)]
- ts-gateway-service→ts-payment-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-payment-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-payment-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route payment (ts-gateway-service/src/main/resources/application.yml:121) or in default-filters]
- ts-gateway-service→ts-payment-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-payment-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-payment-service layer 2 (istio) perTryTimeout, ts-payment-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-preserve-other-service

- ts-preserve-other-service deadline = UNKNOWN [UNKNOWN: ts-preserve-other-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-preserve-other-service (ADR 0009)]
- ts-gateway-service→ts-preserve-other-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-preserve-other-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-preserve-other-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route preserve-other (ts-gateway-service/src/main/resources/application.yml:126) or in default-filters]
- ts-gateway-service→ts-preserve-other-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-preserve-other-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-preserve-other-service layer 2 (istio) perTryTimeout, ts-preserve-other-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-preserve-service

- ts-preserve-service deadline = UNKNOWN [UNKNOWN: ts-preserve-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-preserve-service (ADR 0009)]
- ts-gateway-service→ts-preserve-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-preserve-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-preserve-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route preserve (ts-gateway-service/src/main/resources/application.yml:131) or in default-filters]
- ts-gateway-service→ts-preserve-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-preserve-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-preserve-service layer 2 (istio) perTryTimeout, ts-preserve-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-price-service

- ts-price-service deadline = UNKNOWN [UNKNOWN: ts-price-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-price-service (ADR 0009)]
- ts-gateway-service→ts-price-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-price-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-price-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route price (ts-gateway-service/src/main/resources/application.yml:136) or in default-filters]
- ts-gateway-service→ts-price-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-price-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-price-service layer 2 (istio) perTryTimeout, ts-price-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-rebook-service

- ts-rebook-service deadline = UNKNOWN [UNKNOWN: ts-rebook-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-rebook-service (ADR 0009)]
- ts-gateway-service→ts-rebook-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-rebook-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-rebook-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route rebook (ts-gateway-service/src/main/resources/application.yml:141) or in default-filters]
- ts-gateway-service→ts-rebook-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-rebook-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-rebook-service layer 2 (istio) perTryTimeout, ts-rebook-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-route-plan-service

- ts-route-plan-service deadline = UNKNOWN [UNKNOWN: ts-route-plan-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-route-plan-service (ADR 0009)]
- ts-gateway-service→ts-route-plan-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-route-plan-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-route-plan-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route route-plan (ts-gateway-service/src/main/resources/application.yml:146) or in default-filters]
- ts-gateway-service→ts-route-plan-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-route-plan-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-route-plan-service layer 2 (istio) perTryTimeout, ts-route-plan-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-route-service

- ts-route-service deadline = UNKNOWN [UNKNOWN: ts-route-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-route-service (ADR 0009)]
- ts-gateway-service→ts-route-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-route-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-route-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route route (ts-gateway-service/src/main/resources/application.yml:151) or in default-filters]
- ts-gateway-service→ts-route-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-route-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-route-service layer 2 (istio) perTryTimeout, ts-route-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-seat-service

- ts-seat-service deadline = UNKNOWN [UNKNOWN: ts-seat-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-seat-service (ADR 0009)]
- ts-gateway-service→ts-seat-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-seat-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-seat-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route seat (ts-gateway-service/src/main/resources/application.yml:156) or in default-filters]
- ts-gateway-service→ts-seat-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-seat-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-seat-service layer 2 (istio) perTryTimeout, ts-seat-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-security-service

- ts-security-service deadline = UNKNOWN [UNKNOWN: ts-security-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-security-service (ADR 0009)]
- ts-gateway-service→ts-security-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-security-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-security-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route security (ts-gateway-service/src/main/resources/application.yml:161) or in default-filters]
- ts-gateway-service→ts-security-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-security-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-security-service layer 2 (istio) perTryTimeout, ts-security-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-station-food-service

- ts-station-food-service deadline = UNKNOWN [UNKNOWN: ts-station-food-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-station-food-service (ADR 0009)]
- ts-gateway-service→ts-station-food-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-station-food-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-station-food-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route station-food (ts-gateway-service/src/main/resources/application.yml:166) or in default-filters]
- ts-gateway-service→ts-station-food-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-station-food-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-station-food-service layer 2 (istio) perTryTimeout, ts-station-food-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-station-service

- ts-station-service deadline = UNKNOWN [UNKNOWN: ts-station-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-station-service (ADR 0009)]
- ts-gateway-service→ts-station-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-station-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-station-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route station (ts-gateway-service/src/main/resources/application.yml:171) or in default-filters]
- ts-gateway-service→ts-station-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-station-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-station-service layer 2 (istio) perTryTimeout, ts-station-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-train-food-service

- ts-train-food-service deadline = UNKNOWN [UNKNOWN: ts-train-food-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-train-food-service (ADR 0009)]
- ts-gateway-service→ts-train-food-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-train-food-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-train-food-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route train-food (ts-gateway-service/src/main/resources/application.yml:176) or in default-filters]
- ts-gateway-service→ts-train-food-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-train-food-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-train-food-service layer 2 (istio) perTryTimeout, ts-train-food-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-train-service

- ts-train-service deadline = UNKNOWN [UNKNOWN: ts-train-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-train-service (ADR 0009)]
- ts-gateway-service→ts-train-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-train-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-train-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route train (ts-gateway-service/src/main/resources/application.yml:181) or in default-filters]
- ts-gateway-service→ts-train-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-train-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-train-service layer 2 (istio) perTryTimeout, ts-train-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-travel-plan-service

- ts-travel-plan-service deadline = UNKNOWN [UNKNOWN: ts-travel-plan-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-travel-plan-service (ADR 0009)]
- ts-gateway-service→ts-travel-plan-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-travel-plan-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-travel-plan-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route travel-plan (ts-gateway-service/src/main/resources/application.yml:191) or in default-filters]
- ts-gateway-service→ts-travel-plan-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-travel-plan-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-travel-plan-service layer 2 (istio) perTryTimeout, ts-travel-plan-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-travel-service

- ts-travel-service deadline = UNKNOWN [UNKNOWN: ts-travel-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-travel-service (ADR 0009)]
- ts-gateway-service→ts-travel-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-travel-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-travel-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route travel (ts-gateway-service/src/main/resources/application.yml:196) or in default-filters]
- ts-gateway-service→ts-travel-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-travel-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-travel-service layer 2 (istio) perTryTimeout, ts-travel-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-travel2-service

- ts-travel2-service deadline = UNKNOWN [UNKNOWN: ts-travel2-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-travel2-service (ADR 0009)]
- ts-gateway-service→ts-travel2-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-travel2-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-travel2-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route travel2 (ts-gateway-service/src/main/resources/application.yml:186) or in default-filters]
- ts-gateway-service→ts-travel2-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-travel2-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-travel2-service layer 2 (istio) perTryTimeout, ts-travel2-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-user-service

- ts-user-service deadline = UNKNOWN [UNKNOWN: ts-user-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-user-service (ADR 0009)]
- ts-gateway-service→ts-user-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-user-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-user-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route user (ts-gateway-service/src/main/resources/application.yml:201) or in default-filters]
- ts-gateway-service→ts-user-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-user-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-user-service layer 2 (istio) perTryTimeout, ts-user-service deadline**

### INFO PLM-A2b ts-gateway-service→ts-verification-code-service

- ts-verification-code-service deadline = UNKNOWN [UNKNOWN: ts-verification-code-service has no edges hint; set 'deadline:' in the edges-hint.yaml of ts-verification-code-service (ADR 0009)]
- ts-gateway-service→ts-verification-code-service layer 1 (spring-cloud-gateway) perTryTimeout = UNKNOWN [UNKNOWN: spring.cloud.gateway.httpclient.response-timeout not set; no verified spring-cloud-gateway default for it]
- ts-gateway-service→ts-verification-code-service layer 2 (istio) perTryTimeout = UNKNOWN [UNKNOWN: no per-try timeout: no VirtualService for the host and no VirtualService (Istio default: no timeout); istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry]
- ts-gateway-service→ts-verification-code-service layer 1 (spring-cloud-gateway) totalAttempts = 1 [EXPLICIT: no Retry filter on route verification-code (ts-gateway-service/src/main/resources/application.yml:206) or in default-filters]
- ts-gateway-service→ts-verification-code-service layer 2 (istio) totalAttempts = 3 [LIBRARY_DEFAULT: no VirtualService for the host; istio 1.31 default (verified by doc), https://istio.io/v1.31/docs/reference/config/networking/virtual-service/#HTTPRetry (attempts 2 + 1)]
- **depends-on-unknown: ts-gateway-service→ts-verification-code-service layer 1 (spring-cloud-gateway) perTryTimeout, ts-gateway-service→ts-verification-code-service layer 2 (istio) perTryTimeout, ts-verification-code-service deadline**

## Coverage

Source: collected from 44 service modules.

### Sources read

| Service | Sources |
| --- | --- |
| Json2Shiviz | old-docs/Json2Shiviz/build.gradle, old-docs/Json2Shiviz/src/main/java/ |
| ms-monitoring-core | old-docs/Lib/ms-monitoring-core/pom.xml, old-docs/Lib/ms-monitoring-core/src/main/java/ |
| ts-admin-basic-info-service | ts-admin-basic-info-service/pom.xml, ts-admin-basic-info-service/src/main/java/, ts-admin-basic-info-service/src/main/resources/application.yml |
| ts-admin-order-service | ts-admin-order-service/pom.xml, ts-admin-order-service/src/main/java/, ts-admin-order-service/src/main/resources/application.yml |
| ts-admin-route-service | ts-admin-route-service/pom.xml, ts-admin-route-service/src/main/java/, ts-admin-route-service/src/main/resources/application.yml |
| ts-admin-travel-service | ts-admin-travel-service/pom.xml, ts-admin-travel-service/src/main/java/, ts-admin-travel-service/src/main/resources/application.yml |
| ts-admin-user-service | ts-admin-user-service/pom.xml, ts-admin-user-service/src/main/java/, ts-admin-user-service/src/main/resources/application.yml |
| ts-assurance-service | ts-assurance-service/pom.xml, ts-assurance-service/src/main/java/, ts-assurance-service/src/main/resources/application.yml |
| ts-auth-service | ts-auth-service/pom.xml, ts-auth-service/src/main/java/, ts-auth-service/src/main/resources/application.yaml |
| ts-avatar-service | none (no module in the directory) |
| ts-basic-service | ts-basic-service/pom.xml, ts-basic-service/src/main/java/, ts-basic-service/src/main/resources/application.yml |
| ts-cancel-service | ts-cancel-service/pom.xml, ts-cancel-service/src/main/java/, ts-cancel-service/src/main/resources/application.yml |
| ts-common | ts-common/pom.xml, ts-common/src/main/java/ |
| ts-config-service | ts-config-service/pom.xml, ts-config-service/src/main/java/, ts-config-service/src/main/resources/application.yml |
| ts-consign-price-service | ts-consign-price-service/pom.xml, ts-consign-price-service/src/main/java/, ts-consign-price-service/src/main/resources/application.yml |
| ts-consign-service | ts-consign-service/pom.xml, ts-consign-service/src/main/java/, ts-consign-service/src/main/resources/application.yml |
| ts-contacts-service | ts-contacts-service/pom.xml, ts-contacts-service/src/main/java/, ts-contacts-service/src/main/resources/application.yml |
| ts-delivery-service | ts-delivery-service/pom.xml, ts-delivery-service/src/main/java/, ts-delivery-service/src/main/resources/application.yml |
| ts-execute-service | ts-execute-service/pom.xml, ts-execute-service/src/main/java/, ts-execute-service/src/main/resources/application.yml |
| ts-food-delivery-service | ts-food-delivery-service/pom.xml, ts-food-delivery-service/src/main/java/, ts-food-delivery-service/src/main/resources/application.yml |
| ts-food-service | ts-food-service/pom.xml, ts-food-service/src/main/java/, ts-food-service/src/main/resources/application.yml |
| ts-gateway-service | ts-gateway-service/pom.xml, ts-gateway-service/src/main/java/, ts-gateway-service/src/main/resources/application.yml |
| ts-inside-payment-service | ts-inside-payment-service/pom.xml, ts-inside-payment-service/src/main/java/, ts-inside-payment-service/src/main/resources/application.yml |
| ts-notification-service | ts-notification-service/pom.xml, ts-notification-service/src/main/java/, ts-notification-service/src/main/resources/application.yml |
| ts-order-other-service | ts-order-other-service/pom.xml, ts-order-other-service/src/main/java/, ts-order-other-service/src/main/resources/application.yml |
| ts-order-service | ts-order-service/pom.xml, ts-order-service/src/main/java/, ts-order-service/src/main/resources/application.yml |
| ts-payment-service | ts-payment-service/pom.xml, ts-payment-service/src/main/java/, ts-payment-service/src/main/resources/application.yml |
| ts-preserve-other-service | ts-preserve-other-service/pom.xml, ts-preserve-other-service/src/main/java/, ts-preserve-other-service/src/main/resources/application.yml |
| ts-preserve-service | ts-preserve-service/pom.xml, ts-preserve-service/src/main/java/, ts-preserve-service/src/main/resources/application.yml |
| ts-price-service | ts-price-service/pom.xml, ts-price-service/src/main/java/, ts-price-service/src/main/resources/application.yml |
| ts-rebook-service | ts-rebook-service/pom.xml, ts-rebook-service/src/main/java/, ts-rebook-service/src/main/resources/application.yml |
| ts-route-plan-service | ts-route-plan-service/pom.xml, ts-route-plan-service/src/main/java/, ts-route-plan-service/src/main/resources/application.yml |
| ts-route-service | ts-route-service/pom.xml, ts-route-service/src/main/java/, ts-route-service/src/main/resources/application.yml |
| ts-seat-service | ts-seat-service/pom.xml, ts-seat-service/src/main/java/, ts-seat-service/src/main/resources/application.yml |
| ts-security-service | ts-security-service/pom.xml, ts-security-service/src/main/java/, ts-security-service/src/main/resources/application.yml |
| ts-station-food-service | ts-station-food-service/pom.xml, ts-station-food-service/src/main/java/, ts-station-food-service/src/main/resources/application.yml |
| ts-station-service | ts-station-service/pom.xml, ts-station-service/src/main/java/, ts-station-service/src/main/resources/application.yml |
| ts-train-food-service | ts-train-food-service/pom.xml, ts-train-food-service/src/main/java/, ts-train-food-service/src/main/resources/application.yml |
| ts-train-service | ts-train-service/pom.xml, ts-train-service/src/main/java/, ts-train-service/src/main/resources/application.yml |
| ts-travel-plan-service | ts-travel-plan-service/pom.xml, ts-travel-plan-service/src/main/java/, ts-travel-plan-service/src/main/resources/application.yml |
| ts-travel-service | ts-travel-service/pom.xml, ts-travel-service/src/main/java/, ts-travel-service/src/main/resources/application.yml |
| ts-travel2-service | ts-travel2-service/pom.xml, ts-travel2-service/src/main/java/, ts-travel2-service/src/main/resources/application.yml |
| ts-user-service | ts-user-service/pom.xml, ts-user-service/src/main/java/, ts-user-service/src/main/resources/application.yaml |
| ts-verification-code-service | ts-verification-code-service/pom.xml, ts-verification-code-service/src/main/java/, ts-verification-code-service/src/main/resources/application.yml |
| ts-wait-order-service | ts-wait-order-service/pom.xml, ts-wait-order-service/src/main/java/, ts-wait-order-service/src/main/resources/application.yml |

Istio manifests: deployment/fault-inject-deployment/destination-rule-fault.yaml, deployment/fault-inject-deployment/virtual-services-fault.yaml, deployment/kubernetes-manifests/k8s-with-istio/trainticket-gateway.yaml.

### Layer kinds searched

| Edge | Operation | Searched | Found |
| --- | --- | --- | --- |
| ts-gateway-service→ts-admin-basic-info-service | /api/v1/adminbasicservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-admin-order-service | /api/v1/adminorderservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-admin-route-service | /api/v1/adminrouteservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-admin-travel-service | /api/v1/admintravelservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-admin-user-service | /api/v1/adminuserservice/users/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-assurance-service | /api/v1/assuranceservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-auth-service | /api/v1/auth/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-auth-service | /api/v1/users/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-avatar-service | /api/v1/avatar/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-basic-service | /api/v1/basicservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-cancel-service | /api/v1/cancelservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-config-service | /api/v1/configservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-consign-price-service | /api/v1/consignpriceservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-consign-service | /api/v1/consignservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-contacts-service | /api/v1/contactservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-execute-service | /api/v1/executeservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-food-service | /api/v1/foodservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-inside-payment-service | /api/v1/inside_pay_service/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-notification-service | /api/v1/notifyservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-order-other-service | /api/v1/orderOtherService/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-order-service | /api/v1/orderservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-payment-service | /api/v1/paymentservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-preserve-other-service | /api/v1/preserveotherservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-preserve-service | /api/v1/preserveservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-price-service | /api/v1/priceservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-rebook-service | /api/v1/rebookservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-route-plan-service | /api/v1/routeplanservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-route-service | /api/v1/routeservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-seat-service | /api/v1/seatservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-security-service | /api/v1/securityservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-station-food-service | /api/v1/stationfoodservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-station-service | /api/v1/stationservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-train-food-service | /api/v1/trainfoodservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-train-service | /api/v1/trainservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-travel-plan-service | /api/v1/travelplanservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-travel-service | /api/v1/travelservice/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-travel2-service | /api/v1/travel2service/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-user-service | /api/v1/userservice/users/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |
| ts-gateway-service→ts-verification-code-service | /api/v1/verifycode/** | spring-cloud-gateway, istio | spring-cloud-gateway, istio |

### Sources not read

| Service | Kind | Reference | Evidence |
| --- | --- | --- | --- |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[0].uri = lb://${ADMIN_BASIC_INFO_SERVICE_HOST:ts-admin-basic-info-service}` | ts-gateway-service/src/main/resources/application.yml:17 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[1].uri = lb://${ADMIN_ORDER_SERVICE_HOST:ts-admin-order-service}` | ts-gateway-service/src/main/resources/application.yml:22 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[2].uri = lb://${ADMIN_ROUTE_SERVICE_HOST:ts-admin-route-service}` | ts-gateway-service/src/main/resources/application.yml:27 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[3].uri = lb://${ADMIN_TRAVEL_SERVICE_HOST:ts-admin-travel-service}` | ts-gateway-service/src/main/resources/application.yml:32 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[4].uri = lb://${ADMIN_USER_SERVICE_HOST:ts-admin-user-service}` | ts-gateway-service/src/main/resources/application.yml:37 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[5].uri = lb://${ASSURANCE_SERVICE_HOST:ts-assurance-service}` | ts-gateway-service/src/main/resources/application.yml:42 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[6].uri = lb://${AUTH_SERVICE_HOST:ts-auth-service}` | ts-gateway-service/src/main/resources/application.yml:47 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[7].uri = lb://${AUTH_SERVICE_HOST:ts-auth-service}` | ts-gateway-service/src/main/resources/application.yml:52 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[8].uri = lb://${AVATAR_SERVICE_HOST:ts-avatar-service}` | ts-gateway-service/src/main/resources/application.yml:57 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[9].uri = lb://${BASIC_SERVICE_HOST:ts-basic-service}` | ts-gateway-service/src/main/resources/application.yml:62 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[10].uri = lb://${CANCEL_SERVICE_HOST:ts-cancel-service}` | ts-gateway-service/src/main/resources/application.yml:67 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[11].uri = lb://${CONFIG_SERVICE_HOST:ts-config-service}` | ts-gateway-service/src/main/resources/application.yml:72 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[12].uri = lb://${CONSIGN_PRICE_SERVICE_HOST:ts-consign-price-service}` | ts-gateway-service/src/main/resources/application.yml:77 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[13].uri = lb://${CONSIGN_SERVICE_HOST:ts-consign-service}` | ts-gateway-service/src/main/resources/application.yml:82 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[14].uri = lb://${CONTACTS_SERVICE_HOST:ts-contacts-service}` | ts-gateway-service/src/main/resources/application.yml:87 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[15].uri = lb://${EXECUTE_SERVICE_HOST:ts-execute-service}` | ts-gateway-service/src/main/resources/application.yml:92 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[16].uri = lb://${FOOD_SERVICE_HOST:ts-food-service}` | ts-gateway-service/src/main/resources/application.yml:97 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[17].uri = lb://${INSIDE_PAYMENT_SERVICE_HOST:ts-inside-payment-service}` | ts-gateway-service/src/main/resources/application.yml:102 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[18].uri = lb://${NOTIFICATION_SERVICE_HOST:ts-notification-service}` | ts-gateway-service/src/main/resources/application.yml:107 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[19].uri = lb://${ORDER_OTHER_SERVICE_HOST:ts-order-other-service}` | ts-gateway-service/src/main/resources/application.yml:112 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[20].uri = lb://${ORDER_SERVICE_HOST:ts-order-service}` | ts-gateway-service/src/main/resources/application.yml:117 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[21].uri = lb://${PAYMENT_SERVICE_HOST:ts-payment-service}` | ts-gateway-service/src/main/resources/application.yml:122 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[22].uri = lb://${PRESERVE_OTHER_SERVICE_HOST:ts-preserve-other-service}` | ts-gateway-service/src/main/resources/application.yml:127 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[23].uri = lb://${PRESERVE_SERVICE_HOST:ts-preserve-service}` | ts-gateway-service/src/main/resources/application.yml:132 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[24].uri = lb://${PRICE_SERVICE_HOST:ts-price-service}` | ts-gateway-service/src/main/resources/application.yml:137 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[25].uri = lb://${REBOOK_SERVICE_HOST:ts-rebook-service}` | ts-gateway-service/src/main/resources/application.yml:142 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[26].uri = lb://${ROUTE_PLAN_SERVICE_HOST:ts-route-plan-service}` | ts-gateway-service/src/main/resources/application.yml:147 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[27].uri = lb://${ROUTE_SERVICE_HOST:ts-route-service}` | ts-gateway-service/src/main/resources/application.yml:152 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[28].uri = lb://${SEAT_SERVICE_HOST:ts-seat-service}` | ts-gateway-service/src/main/resources/application.yml:157 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[29].uri = lb://${SECURITY_SERVICE_HOST:ts-security-service}` | ts-gateway-service/src/main/resources/application.yml:162 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[30].uri = lb://${STATION_FOOD_SERVICE_HOST:ts-station-food-service}` | ts-gateway-service/src/main/resources/application.yml:167 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[31].uri = lb://${STATION_SERVICE_HOST:ts-station-service}` | ts-gateway-service/src/main/resources/application.yml:172 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[32].uri = lb://${TRAIN_FOOD_SERVICE_HOST:ts-train-food-service}` | ts-gateway-service/src/main/resources/application.yml:177 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[33].uri = lb://${TRAIN_SERVICE_HOST:ts-train-service}` | ts-gateway-service/src/main/resources/application.yml:182 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[34].uri = lb://${TRAVEL2_SERVICE_HOST:ts-travel2-service}` | ts-gateway-service/src/main/resources/application.yml:187 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[35].uri = lb://${TRAVEL_PLAN_SERVICE_HOST:ts-travel-plan-service}` | ts-gateway-service/src/main/resources/application.yml:192 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[36].uri = lb://${TRAVEL_SERVICE_HOST:ts-travel-service}` | ts-gateway-service/src/main/resources/application.yml:197 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[37].uri = lb://${USER_SERVICE_HOST:ts-user-service}` | ts-gateway-service/src/main/resources/application.yml:202 |
| ts-gateway-service | placeholder | `spring.cloud.gateway.routes.[38].uri = lb://${VERIFICATION_CODE_SERVICE_HOST:ts-verification-code-service}` | ts-gateway-service/src/main/resources/application.yml:207 |

## Notes

- deployment/fault-inject-deployment/destination-rule-fault.yaml:9: short host ts-voucher-service in a rule without metadata.namespace; its namespace is the one it is applied to, not known here
- deployment/fault-inject-deployment/virtual-services-fault.yaml:7: host ts-voucher-service has 2 HTTP routes; the first one whose match fits a request applies
- deployment/fault-inject-deployment/virtual-services-fault.yaml:7: short host ts-voucher-service in a rule without metadata.namespace; its namespace is the one it is applied to, not known here
- deployment/kubernetes-manifests/k8s-with-istio/trainticket-gateway.yaml:23: VirtualService trainticket binds gateways only, not the mesh; skipped
- ts-admin-basic-info-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:105: adminbasic.service.AdminBasicInfoServiceImpl#getAllStations calls GET {?}/api/v1/stationservice/stations through RestTemplate with a host computed at run time (stations); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:122: adminbasic.service.AdminBasicInfoServiceImpl#addStation calls POST {?}/api/v1/stationservice/stations through RestTemplate with a host computed at run time (stations); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:137: adminbasic.service.AdminBasicInfoServiceImpl#deleteStation calls DELETE {?}/api/v1/stationservice/stations/{id} through RestTemplate with a host computed at run time (path); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:152: adminbasic.service.AdminBasicInfoServiceImpl#modifyStation calls PUT {?}/api/v1/stationservice/stations through RestTemplate with a host computed at run time (stations); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:168: adminbasic.service.AdminBasicInfoServiceImpl#getAllTrains calls GET {?}/api/v1/trainservice/trains through RestTemplate with a host computed at run time (trains); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:184: adminbasic.service.AdminBasicInfoServiceImpl#addTrain calls POST {?}/api/v1/trainservice/trains through RestTemplate with a host computed at run time (trains); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:199: adminbasic.service.AdminBasicInfoServiceImpl#deleteTrain calls DELETE {?}/api/v1/trainservice/trains/{id} through RestTemplate with a host computed at run time (train_service_url + "/api/v1/trainservice/trains/" + id); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:214: adminbasic.service.AdminBasicInfoServiceImpl#modifyTrain calls PUT {?}/api/v1/trainservice/trains through RestTemplate with a host computed at run time (trains); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:228: adminbasic.service.AdminBasicInfoServiceImpl#getAllConfigs calls GET {?}/api/v1/configservice/configs through RestTemplate with a host computed at run time (configs); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:242: adminbasic.service.AdminBasicInfoServiceImpl#addConfig calls POST {?}/api/v1/configservice/configs through RestTemplate with a host computed at run time (configs); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:254: adminbasic.service.AdminBasicInfoServiceImpl#deleteConfig calls DELETE {?}/api/v1/configservice/configs/{name} through RestTemplate with a host computed at run time (config_service_url + "/api/v1/configservice/configs/" + name); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:267: adminbasic.service.AdminBasicInfoServiceImpl#modifyConfig calls PUT {?}/api/v1/configservice/configs through RestTemplate with a host computed at run time (configs); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:280: adminbasic.service.AdminBasicInfoServiceImpl#getAllPrices calls GET {?}/api/v1/priceservice/prices through RestTemplate with a host computed at run time (prices); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:293: adminbasic.service.AdminBasicInfoServiceImpl#addPrice calls POST {?}/api/v1/priceservice/prices through RestTemplate with a host computed at run time (prices); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:307: adminbasic.service.AdminBasicInfoServiceImpl#deletePrice calls DELETE {?}/api/v1/priceservice/prices/{pricesId} through RestTemplate with a host computed at run time (path); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:321: adminbasic.service.AdminBasicInfoServiceImpl#modifyPrice calls PUT {?}/api/v1/priceservice/prices through RestTemplate with a host computed at run time (prices); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:44: adminbasic.service.AdminBasicInfoServiceImpl#getAllContacts calls GET {?}/api/v1/contactservice/contacts through RestTemplate with a host computed at run time (contacts_service_url + "/api/v1/contactservice/contacts"); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:59: adminbasic.service.AdminBasicInfoServiceImpl#deleteContact calls DELETE {?}/api/v1/contactservice/contacts/{contactsId} through RestTemplate with a host computed at run time (contacts_service_url + "/api/v1/contactservice/contacts/" + contactsId); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:74: adminbasic.service.AdminBasicInfoServiceImpl#modifyContact calls PUT {?}/api/v1/contactservice/contacts through RestTemplate with a host computed at run time (contacts_service_url + "/api/v1/contactservice/contacts"); not an edge
- ts-admin-basic-info-service: ts-admin-basic-info-service/src/main/java/adminbasic/service/AdminBasicInfoServiceImpl.java:90: adminbasic.service.AdminBasicInfoServiceImpl#addContact calls POST {?}/api/v1/contactservice/contacts/admin through RestTemplate with a host computed at run time (contacts_service_url + "/api/v1/contactservice/contacts/admin"); not an edge
- ts-admin-order-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-admin-order-service: ts-admin-order-service/src/main/java/adminorder/service/AdminOrderServiceImpl.java:104: adminorder.service.AdminOrderServiceImpl#deleteOrder calls DELETE {?}/api/v1/orderOtherService/orderOther/{orderId} through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther/" + orderId); not an edge
- ts-admin-order-service: ts-admin-order-service/src/main/java/adminorder/service/AdminOrderServiceImpl.java:126: adminorder.service.AdminOrderServiceImpl#updateOrder calls PUT {?}/api/v1/orderservice/order/admin through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order/admin"); not an edge
- ts-admin-order-service: ts-admin-order-service/src/main/java/adminorder/service/AdminOrderServiceImpl.java:137: adminorder.service.AdminOrderServiceImpl#updateOrder calls PUT {?}/api/v1/orderOtherService/orderOther/admin through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther/admin"); not an edge
- ts-admin-order-service: ts-admin-order-service/src/main/java/adminorder/service/AdminOrderServiceImpl.java:157: adminorder.service.AdminOrderServiceImpl#addOrder calls POST {?}/api/v1/orderservice/order/admin through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order/admin"); not an edge
- ts-admin-order-service: ts-admin-order-service/src/main/java/adminorder/service/AdminOrderServiceImpl.java:168: adminorder.service.AdminOrderServiceImpl#addOrder calls POST {?}/api/v1/orderOtherService/orderOther/admin through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther/admin"); not an edge
- ts-admin-order-service: ts-admin-order-service/src/main/java/adminorder/service/AdminOrderServiceImpl.java:48: adminorder.service.AdminOrderServiceImpl#getAllOrders calls GET {?}/api/v1/orderservice/order through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order"); not an edge
- ts-admin-order-service: ts-admin-order-service/src/main/java/adminorder/service/AdminOrderServiceImpl.java:66: adminorder.service.AdminOrderServiceImpl#getAllOrders calls GET {?}/api/v1/orderOtherService/orderOther through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther"); not an edge
- ts-admin-order-service: ts-admin-order-service/src/main/java/adminorder/service/AdminOrderServiceImpl.java:93: adminorder.service.AdminOrderServiceImpl#deleteOrder calls DELETE {?}/api/v1/orderservice/order/{orderId} through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order/" + orderId); not an edge
- ts-admin-route-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-admin-route-service: ts-admin-route-service/src/main/java/adminroute/service/AdminRouteServiceImpl.java:103: adminroute.service.AdminRouteServiceImpl#checkStationsExists calls POST {?}/api/v1/stationservice/stations/idlist through RestTemplate with a host computed at run time (station_service_url + "/api/v1/stationservice/stations/idlist"); not an edge
- ts-admin-route-service: ts-admin-route-service/src/main/java/adminroute/service/AdminRouteServiceImpl.java:40: adminroute.service.AdminRouteServiceImpl#getAllRoutes calls GET {?}/api/v1/routeservice/routes through RestTemplate with a host computed at run time (route_service_url + "/api/v1/routeservice/routes"); not an edge
- ts-admin-route-service: ts-admin-route-service/src/main/java/adminroute/service/AdminRouteServiceImpl.java:70: adminroute.service.AdminRouteServiceImpl#createAndModifyRoute calls POST {?}/api/v1/routeservice/routes through RestTemplate with a host computed at run time (route_service_url + "/api/v1/routeservice/routes"); not an edge
- ts-admin-route-service: ts-admin-route-service/src/main/java/adminroute/service/AdminRouteServiceImpl.java:87: adminroute.service.AdminRouteServiceImpl#deleteRoute calls DELETE {?}/api/v1/routeservice/routes/{routeId} through RestTemplate with a host computed at run time (route_service_url + "/api/v1/routeservice/routes/" + routeId); not an edge
- ts-admin-travel-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-admin-travel-service: ts-admin-travel-service/src/main/java/admintravel/service/AdminTravelServiceImpl.java:107: admintravel.service.AdminTravelServiceImpl#addTravel calls POST {requestUrl} through RestTemplate with a host computed at run time (requestUrl); not an edge
- ts-admin-travel-service: ts-admin-travel-service/src/main/java/admintravel/service/AdminTravelServiceImpl.java:142: admintravel.service.AdminTravelServiceImpl#updateTravel calls PUT  through RestTemplate with a host computed at run time (requestUrl); not an edge
- ts-admin-travel-service: ts-admin-travel-service/src/main/java/admintravel/service/AdminTravelServiceImpl.java:171: admintravel.service.AdminTravelServiceImpl#deleteTravel calls DELETE  through RestTemplate with a host computed at run time (requestUtl); not an edge
- ts-admin-travel-service: ts-admin-travel-service/src/main/java/admintravel/service/AdminTravelServiceImpl.java:233: admintravel.service.AdminTravelServiceImpl#checkStationsExists calls POST {?}/api/v1/stationservice/stations/idlist through RestTemplate with a host computed at run time (station_service_url + "/api/v1/stationservice/stations/idlist"); not an edge
- ts-admin-travel-service: ts-admin-travel-service/src/main/java/admintravel/service/AdminTravelServiceImpl.java:260: admintravel.service.AdminTravelServiceImpl#queryTrainTypeByName calls GET {?}/api/v1/trainservice/trains/byName/{trainTypeName} through RestTemplate with a host computed at run time (train_service_url + "/api/v1/trainservice/trains/byName/" + trainTypeName); not an edge
- ts-admin-travel-service: ts-admin-travel-service/src/main/java/admintravel/service/AdminTravelServiceImpl.java:274: admintravel.service.AdminTravelServiceImpl#getRouteByRouteId calls GET {?}/api/v1/routeservice/routes/{routeId} through RestTemplate with a host computed at run time (route_service_url + "/api/v1/routeservice/routes/" + routeId); not an edge
- ts-admin-travel-service: ts-admin-travel-service/src/main/java/admintravel/service/AdminTravelServiceImpl.java:49: admintravel.service.AdminTravelServiceImpl#getAllTravels calls GET {?}/api/v1/travelservice/admin_trip through RestTemplate with a host computed at run time (travel_service_url + "/api/v1/travelservice/admin_trip"); not an edge
- ts-admin-travel-service: ts-admin-travel-service/src/main/java/admintravel/service/AdminTravelServiceImpl.java:67: admintravel.service.AdminTravelServiceImpl#getAllTravels calls GET {?}/api/v1/travel2service/admin_trip through RestTemplate with a host computed at run time (travel2_service_url + "/api/v1/travel2service/admin_trip"); not an edge
- ts-admin-user-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-admin-user-service: ts-admin-user-service/src/main/java/adminuser/service/AdminUserServiceImpl.java:115: adminuser.service.AdminUserServiceImpl#addUser calls POST {?}/api/v1/userservice/users/register through RestTemplate with a host computed at run time (USER_SERVICE_IP_URI + "/register"); not an edge
- ts-admin-user-service: ts-admin-user-service/src/main/java/adminuser/service/AdminUserServiceImpl.java:45: adminuser.service.AdminUserServiceImpl#getAllUsers calls GET {?}/api/v1/userservice/users through RestTemplate with a host computed at run time (USER_SERVICE_IP_URI); not an edge
- ts-admin-user-service: ts-admin-user-service/src/main/java/adminuser/service/AdminUserServiceImpl.java:70: adminuser.service.AdminUserServiceImpl#deleteUser calls DELETE {?}/api/v1/userservice/users/{userId} through RestTemplate with a host computed at run time (USER_SERVICE_IP_URI + "/" + userId); not an edge
- ts-admin-user-service: ts-admin-user-service/src/main/java/adminuser/service/AdminUserServiceImpl.java:94: adminuser.service.AdminUserServiceImpl#updateUser calls PUT {?}/api/v1/userservice/users through RestTemplate with a host computed at run time (USER_SERVICE_IP_URI); not an edge
- ts-assurance-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-auth-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-auth-service: ts-auth-service/src/main/java/auth/service/impl/TokenServiceImpl.java:67: auth.service.impl.TokenServiceImpl#getToken calls GET {?}/api/v1/verifycode/verify/{?} through RestTemplate with a host computed at run time (verification_code_service_url + "/api/v1/verifycode/verify/" + verifyCode); not an edge
- ts-avatar-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-basic-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-basic-service: ts-basic-service/src/main/java/fdse/microservice/service/BasicServiceImpl.java:328: fdse.microservice.service.BasicServiceImpl#queryForStationId calls GET {?}/api/v1/stationservice/stations/id/{stationName} through RestTemplate with a host computed at run time (station_service_url + "/api/v1/stationservice/stations/id/" + stationName); not an edge
- ts-basic-service: ts-basic-service/src/main/java/fdse/microservice/service/BasicServiceImpl.java:345: fdse.microservice.service.BasicServiceImpl#checkStationsExists calls POST {?}/api/v1/stationservice/stations/idlist through RestTemplate with a host computed at run time (station_service_url + "/api/v1/stationservice/stations/idlist"); not an edge
- ts-basic-service: ts-basic-service/src/main/java/fdse/microservice/service/BasicServiceImpl.java:362: fdse.microservice.service.BasicServiceImpl#checkStationExists calls GET {?}/api/v1/stationservice/stations/id/{stationName} through RestTemplate with a host computed at run time (station_service_url + "/api/v1/stationservice/stations/id/" + stationName); not an edge
- ts-basic-service: ts-basic-service/src/main/java/fdse/microservice/service/BasicServiceImpl.java:376: fdse.microservice.service.BasicServiceImpl#queryTrainTypeByNames calls POST {?}/api/v1/trainservice/trains/byNames through RestTemplate with a host computed at run time (train_service_url + "/api/v1/trainservice/trains/byNames"); not an edge
- ts-basic-service: ts-basic-service/src/main/java/fdse/microservice/service/BasicServiceImpl.java:393: fdse.microservice.service.BasicServiceImpl#queryTrainTypeByName calls GET {?}/api/v1/trainservice/trains/byName/{trainTypeName} through RestTemplate with a host computed at run time (train_service_url + "/api/v1/trainservice/trains/byName/" + trainTypeName); not an edge
- ts-basic-service: ts-basic-service/src/main/java/fdse/microservice/service/BasicServiceImpl.java:407: fdse.microservice.service.BasicServiceImpl#getRoutesByRouteIds calls POST {?}/api/v1/routeservice/routes/byIds/ through RestTemplate with a host computed at run time (route_service_url + "/api/v1/routeservice/routes/byIds/"); not an edge
- ts-basic-service: ts-basic-service/src/main/java/fdse/microservice/service/BasicServiceImpl.java:427: fdse.microservice.service.BasicServiceImpl#getRouteByRouteId calls GET {?}/api/v1/routeservice/routes/{routeId} through RestTemplate with a host computed at run time (route_service_url + "/api/v1/routeservice/routes/" + routeId); not an edge
- ts-basic-service: ts-basic-service/src/main/java/fdse/microservice/service/BasicServiceImpl.java:446: fdse.microservice.service.BasicServiceImpl#queryPriceConfigByRouteIdAndTrainType calls GET {?}/api/v1/priceservice/prices/{routeId}/{trainType} through RestTemplate with a host computed at run time (price_service_url + "/api/v1/priceservice/prices/" + routeId + "/" + trainType); not an edge
- ts-basic-service: ts-basic-service/src/main/java/fdse/microservice/service/BasicServiceImpl.java:461: fdse.microservice.service.BasicServiceImpl#queryPriceConfigByRouteIdsAndTrainTypes calls POST {?}/api/v1/priceservice/prices/byRouteIdsAndTrainTypes through RestTemplate with a host computed at run time (price_service_url + "/api/v1/priceservice/prices/byRouteIdsAndTrainTypes"); not an edge
- ts-cancel-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-cancel-service: ts-cancel-service/src/main/java/cancel/service/CancelServiceImpl.java:148: cancel.service.CancelServiceImpl#sendEmail calls POST {?}/api/v1/notifyservice/notification/order_cancel_success through RestTemplate with a host computed at run time (notification_service_url + "/api/v1/notifyservice/notification/order_cancel_success"); not an edge
- ts-cancel-service: ts-cancel-service/src/main/java/cancel/service/CancelServiceImpl.java:245: cancel.service.CancelServiceImpl#cancelFromOrder calls PUT {?}/api/v1/orderservice/order through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order"); not an edge
- ts-cancel-service: ts-cancel-service/src/main/java/cancel/service/CancelServiceImpl.java:269: cancel.service.CancelServiceImpl#cancelFromOtherOrder calls PUT {?}/api/v1/orderOtherService/orderOther through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther"); not an edge
- ts-cancel-service: ts-cancel-service/src/main/java/cancel/service/CancelServiceImpl.java:284: cancel.service.CancelServiceImpl#drawbackMoney calls GET {?}/api/v1/inside_pay_service/inside_payment/drawback/{userId}/{money} through RestTemplate with a host computed at run time (inside_payment_service_url + "/api/v1/inside_pay_service/inside_payment/drawback/" + userId + "/" + money); not an edge
- ts-cancel-service: ts-cancel-service/src/main/java/cancel/service/CancelServiceImpl.java:299: cancel.service.CancelServiceImpl#getAccount calls GET {?}/api/v1/userservice/users/id/{orderId} through RestTemplate with a host computed at run time (user_service_url + "/api/v1/userservice/users/id/" + orderId); not an edge
- ts-cancel-service: ts-cancel-service/src/main/java/cancel/service/CancelServiceImpl.java:313: cancel.service.CancelServiceImpl#getOrderByIdFromOrder calls GET {?}/api/v1/orderservice/order/{orderId} through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order/" + orderId); not an edge
- ts-cancel-service: ts-cancel-service/src/main/java/cancel/service/CancelServiceImpl.java:327: cancel.service.CancelServiceImpl#getOrderByIdFromOrderOther calls GET {?}/api/v1/orderOtherService/orderOther/{orderId} through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther/" + orderId); not an edge
- ts-config-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-consign-price-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-consign-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-consign-service: ts-consign-service/src/main/java/consign/service/ConsignServiceImpl.java:100: consign.service.ConsignServiceImpl#updateConsignRecord calls GET {?}/api/v1/consignpriceservice/consignprice/{?}/{?} through RestTemplate with a host computed at run time (consign_price_service_url + "/api/v1/consignpriceservice/consignprice/" + consignRequest.getWeight() + "/" + consignRequest.isWithin()); not an edge
- ts-consign-service: ts-consign-service/src/main/java/consign/service/ConsignServiceImpl.java:67: consign.service.ConsignServiceImpl#insertConsignRecord calls GET {?}/api/v1/consignpriceservice/consignprice/{?}/{?} through RestTemplate with a host computed at run time (consign_price_service_url + "/api/v1/consignpriceservice/consignprice/" + consignRequest.getWeight() + "/" + consignRequest.isWithin()); not an edge
- ts-contacts-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-execute-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-execute-service: ts-execute-service/src/main/java/execute/serivce/ExecuteServiceImpl.java:141: execute.serivce.ExecuteServiceImpl#executeOrder calls GET {?}/api/v1/orderservice/order/status/{orderId}/{status} through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order/status/" + orderId + "/" + status); not an edge
- ts-execute-service: ts-execute-service/src/main/java/execute/serivce/ExecuteServiceImpl.java:155: execute.serivce.ExecuteServiceImpl#executeOrderOther calls GET {?}/api/v1/orderOtherService/orderOther/status/{orderId}/{status} through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther/status/" + orderId + "/" + status); not an edge
- ts-execute-service: ts-execute-service/src/main/java/execute/serivce/ExecuteServiceImpl.java:168: execute.serivce.ExecuteServiceImpl#getOrderByIdFromOrder calls GET {?}/api/v1/orderservice/order/{orderId} through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order/" + orderId); not an edge
- ts-execute-service: ts-execute-service/src/main/java/execute/serivce/ExecuteServiceImpl.java:182: execute.serivce.ExecuteServiceImpl#getOrderByIdFromOrderOther calls GET {?}/api/v1/orderOtherService/orderOther/{orderId} through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther/" + orderId); not an edge
- ts-food-delivery-service: ts-food-delivery-service/src/main/java/food_delivery/service/FoodDeliveryServiceImpl.java:49: food_delivery.service.FoodDeliveryServiceImpl#createFoodDeliveryOrder calls GET {?}/api/v1/stationfoodservice/stationfoodstores/bystoreid/{?} through RestTemplate with a host computed at run time (staion_food_service_url + "/api/v1/stationfoodservice/stationfoodstores/bystoreid/" + stationFoodStoreId); not an edge
- ts-food-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-food-service: ts-food-service/src/main/java/foodsearch/service/FoodServiceImpl.java:229: foodsearch.service.FoodServiceImpl#getAllFood calls GET {?}/api/v1/trainfoodservice/trainfoods/{tripId} through RestTemplate with a host computed at run time (train_food_service_url + "/api/v1/trainfoodservice/trainfoods/" + tripId); not an edge
- ts-food-service: ts-food-service/src/main/java/foodsearch/service/FoodServiceImpl.java:251: foodsearch.service.FoodServiceImpl#getAllFood calls GET {?}/api/v1/travelservice/routes/{tripId} through RestTemplate with a host computed at run time (travel_service_url + "/api/v1/travelservice/routes/" + tripId); not an edge
- ts-food-service: ts-food-service/src/main/java/foodsearch/service/FoodServiceImpl.java:286: foodsearch.service.FoodServiceImpl#getAllFood calls POST {?}/api/v1/stationfoodservice/stationfoodstores through RestTemplate with a host computed at run time (station_food_service_url + "/api/v1/stationfoodservice/stationfoodstores"); not an edge
- ts-gateway-service: entry, Spring Cloud Gateway routing to ts-admin-basic-info-service, ts-admin-order-service, ts-admin-route-service, ts-admin-travel-service, ts-admin-user-service, ts-assurance-service, ts-auth-service, ts-avatar-service, ts-basic-service, ts-cancel-service, ts-config-service, ts-consign-price-service, ts-consign-service, ts-contacts-service, ts-execute-service, ts-food-service, ts-inside-payment-service, ts-notification-service, ts-order-other-service, ts-order-service, ts-payment-service, ts-preserve-other-service, ts-preserve-service, ts-price-service, ts-rebook-service, ts-route-plan-service, ts-route-service, ts-seat-service, ts-security-service, ts-station-food-service, ts-station-service, ts-train-food-service, ts-train-service, ts-travel-plan-service, ts-travel-service, ts-travel2-service, ts-user-service, ts-verification-code-service (ADR 0009)
- ts-gateway-service: routes read from the legacy prefix spring.cloud.gateway.*; Spring Cloud Gateway 5 reads spring.cloud.gateway.server.webflux.*
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:101: route inside-payment goes to lb://${INSIDE_PAYMENT_SERVICE_HOST:ts-inside-payment-service}; the callee is ts-inside-payment-service, the placeholder default; setting INSIDE_PAYMENT_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:106: route notification goes to lb://${NOTIFICATION_SERVICE_HOST:ts-notification-service}; the callee is ts-notification-service, the placeholder default; setting NOTIFICATION_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:111: route order-other goes to lb://${ORDER_OTHER_SERVICE_HOST:ts-order-other-service}; the callee is ts-order-other-service, the placeholder default; setting ORDER_OTHER_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:116: route order goes to lb://${ORDER_SERVICE_HOST:ts-order-service}; the callee is ts-order-service, the placeholder default; setting ORDER_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:121: route payment goes to lb://${PAYMENT_SERVICE_HOST:ts-payment-service}; the callee is ts-payment-service, the placeholder default; setting PAYMENT_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:126: route preserve-other goes to lb://${PRESERVE_OTHER_SERVICE_HOST:ts-preserve-other-service}; the callee is ts-preserve-other-service, the placeholder default; setting PRESERVE_OTHER_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:131: route preserve goes to lb://${PRESERVE_SERVICE_HOST:ts-preserve-service}; the callee is ts-preserve-service, the placeholder default; setting PRESERVE_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:136: route price goes to lb://${PRICE_SERVICE_HOST:ts-price-service}; the callee is ts-price-service, the placeholder default; setting PRICE_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:141: route rebook goes to lb://${REBOOK_SERVICE_HOST:ts-rebook-service}; the callee is ts-rebook-service, the placeholder default; setting REBOOK_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:146: route route-plan goes to lb://${ROUTE_PLAN_SERVICE_HOST:ts-route-plan-service}; the callee is ts-route-plan-service, the placeholder default; setting ROUTE_PLAN_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:151: route route goes to lb://${ROUTE_SERVICE_HOST:ts-route-service}; the callee is ts-route-service, the placeholder default; setting ROUTE_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:156: route seat goes to lb://${SEAT_SERVICE_HOST:ts-seat-service}; the callee is ts-seat-service, the placeholder default; setting SEAT_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:161: route security goes to lb://${SECURITY_SERVICE_HOST:ts-security-service}; the callee is ts-security-service, the placeholder default; setting SECURITY_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:166: route station-food goes to lb://${STATION_FOOD_SERVICE_HOST:ts-station-food-service}; the callee is ts-station-food-service, the placeholder default; setting STATION_FOOD_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:16: route admin-basic-info goes to lb://${ADMIN_BASIC_INFO_SERVICE_HOST:ts-admin-basic-info-service}; the callee is ts-admin-basic-info-service, the placeholder default; setting ADMIN_BASIC_INFO_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:171: route station goes to lb://${STATION_SERVICE_HOST:ts-station-service}; the callee is ts-station-service, the placeholder default; setting STATION_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:176: route train-food goes to lb://${TRAIN_FOOD_SERVICE_HOST:ts-train-food-service}; the callee is ts-train-food-service, the placeholder default; setting TRAIN_FOOD_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:181: route train goes to lb://${TRAIN_SERVICE_HOST:ts-train-service}; the callee is ts-train-service, the placeholder default; setting TRAIN_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:186: route travel2 goes to lb://${TRAVEL2_SERVICE_HOST:ts-travel2-service}; the callee is ts-travel2-service, the placeholder default; setting TRAVEL2_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:191: route travel-plan goes to lb://${TRAVEL_PLAN_SERVICE_HOST:ts-travel-plan-service}; the callee is ts-travel-plan-service, the placeholder default; setting TRAVEL_PLAN_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:196: route travel goes to lb://${TRAVEL_SERVICE_HOST:ts-travel-service}; the callee is ts-travel-service, the placeholder default; setting TRAVEL_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:201: route user goes to lb://${USER_SERVICE_HOST:ts-user-service}; the callee is ts-user-service, the placeholder default; setting USER_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:206: route verification-code goes to lb://${VERIFICATION_CODE_SERVICE_HOST:ts-verification-code-service}; the callee is ts-verification-code-service, the placeholder default; setting VERIFICATION_CODE_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:21: route admin-order goes to lb://${ADMIN_ORDER_SERVICE_HOST:ts-admin-order-service}; the callee is ts-admin-order-service, the placeholder default; setting ADMIN_ORDER_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:26: route admin-route goes to lb://${ADMIN_ROUTE_SERVICE_HOST:ts-admin-route-service}; the callee is ts-admin-route-service, the placeholder default; setting ADMIN_ROUTE_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:31: route admin-travel goes to lb://${ADMIN_TRAVEL_SERVICE_HOST:ts-admin-travel-service}; the callee is ts-admin-travel-service, the placeholder default; setting ADMIN_TRAVEL_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:36: route admin-user goes to lb://${ADMIN_USER_SERVICE_HOST:ts-admin-user-service}; the callee is ts-admin-user-service, the placeholder default; setting ADMIN_USER_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:41: route assurance goes to lb://${ASSURANCE_SERVICE_HOST:ts-assurance-service}; the callee is ts-assurance-service, the placeholder default; setting ASSURANCE_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:46: route auth goes to lb://${AUTH_SERVICE_HOST:ts-auth-service}; the callee is ts-auth-service, the placeholder default; setting AUTH_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:51: route auth-user goes to lb://${AUTH_SERVICE_HOST:ts-auth-service}; the callee is ts-auth-service, the placeholder default; setting AUTH_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:56: route avatar goes to lb://${AVATAR_SERVICE_HOST:ts-avatar-service}; the callee is ts-avatar-service, the placeholder default; setting AVATAR_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:61: route basic goes to lb://${BASIC_SERVICE_HOST:ts-basic-service}; the callee is ts-basic-service, the placeholder default; setting BASIC_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:66: route cancel goes to lb://${CANCEL_SERVICE_HOST:ts-cancel-service}; the callee is ts-cancel-service, the placeholder default; setting CANCEL_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:71: route config goes to lb://${CONFIG_SERVICE_HOST:ts-config-service}; the callee is ts-config-service, the placeholder default; setting CONFIG_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:76: route consign-price goes to lb://${CONSIGN_PRICE_SERVICE_HOST:ts-consign-price-service}; the callee is ts-consign-price-service, the placeholder default; setting CONSIGN_PRICE_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:81: route consign goes to lb://${CONSIGN_SERVICE_HOST:ts-consign-service}; the callee is ts-consign-service, the placeholder default; setting CONSIGN_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:86: route contacts goes to lb://${CONTACTS_SERVICE_HOST:ts-contacts-service}; the callee is ts-contacts-service, the placeholder default; setting CONTACTS_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:91: route execute goes to lb://${EXECUTE_SERVICE_HOST:ts-execute-service}; the callee is ts-execute-service, the placeholder default; setting EXECUTE_SERVICE_HOST changes it
- ts-gateway-service: ts-gateway-service/src/main/resources/application.yml:96: route food goes to lb://${FOOD_SERVICE_HOST:ts-food-service}; the callee is ts-food-service, the placeholder default; setting FOOD_SERVICE_HOST changes it
- ts-inside-payment-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-inside-payment-service: ts-inside-payment-service/src/main/java/inside_payment/async/AsyncTask.java:27: inside_payment.async.AsyncTask#sendAsyncCallToPaymentService calls GET /greet through RestTemplate on rest-service-external, which is not a service of this directory; not an edge
- ts-inside-payment-service: ts-inside-payment-service/src/main/java/inside_payment/service/InsidePaymentServiceImpl.java:110: inside_payment.service.InsidePaymentServiceImpl#pay calls POST {?}/api/v1/paymentservice/payment through RestTemplate with a host computed at run time (payment_service_url + "/api/v1/paymentservice/payment"); not an edge
- ts-inside-payment-service: ts-inside-payment-service/src/main/java/inside_payment/service/InsidePaymentServiceImpl.java:297: inside_payment.service.InsidePaymentServiceImpl#payDifference calls POST {?}/api/v1/paymentservice/payment through RestTemplate with a host computed at run time (payment_service_url + "/api/v1/paymentservice/payment"); not an edge
- ts-inside-payment-service: ts-inside-payment-service/src/main/java/inside_payment/service/InsidePaymentServiceImpl.java:339: inside_payment.service.InsidePaymentServiceImpl#setOrderStatus calls GET {?}/api/v1/orderservice/order/status/{orderId}/{?} through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order/status/" + orderId + "/" + orderStatus); not an edge
- ts-inside-payment-service: ts-inside-payment-service/src/main/java/inside_payment/service/InsidePaymentServiceImpl.java:349: inside_payment.service.InsidePaymentServiceImpl#setOrderStatus calls GET {?}/api/v1/orderOtherService/orderOther/status/{orderId}/{?} through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther/status/" + orderId + "/" + orderStatus); not an edge
- ts-inside-payment-service: ts-inside-payment-service/src/main/java/inside_payment/service/InsidePaymentServiceImpl.java:59: inside_payment.service.InsidePaymentServiceImpl#pay calls GET  through RestTemplate with a host computed at run time (requestOrderURL); not an edge
- ts-notification-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-order-other-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-order-other-service: ts-order-other-service/src/main/java/other/service/OrderOtherServiceImpl.java:227: other.service.OrderOtherServiceImpl#queryForStationId calls POST {?}/api/v1/stationservice/stations/namelist through RestTemplate with a host computed at run time (station_service_url + "/api/v1/stationservice/stations/namelist"); not an edge
- ts-order-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-order-service: ts-order-service/src/main/java/order/service/OrderServiceImpl.java:212: order.service.OrderServiceImpl#queryForStationId calls POST {?}/api/v1/stationservice/stations/namelist through RestTemplate with a host computed at run time (station_service_url + "/api/v1/stationservice/stations/namelist"); not an edge
- ts-payment-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-preserve-other-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-preserve-other-service: ts-preserve-other-service/src/main/java/preserveOther/service/PreserveOtherServiceImpl.java:132: preserveOther.service.PreserveOtherServiceImpl#preserve calls POST {?}/api/v1/basicservice/basic/travel through RestTemplate with a host computed at run time (basic_service_url + "/api/v1/basicservice/basic/travel"); not an edge
- ts-preserve-other-service: ts-preserve-other-service/src/main/java/preserveOther/service/PreserveOtherServiceImpl.java:277: preserveOther.service.PreserveOtherServiceImpl#dipatchSeat calls POST {?}/api/v1/seatservice/seats through RestTemplate with a host computed at run time (seat_service_url + "/api/v1/seatservice/seats"); not an edge
- ts-preserve-other-service: ts-preserve-other-service/src/main/java/preserveOther/service/PreserveOtherServiceImpl.java:305: preserveOther.service.PreserveOtherServiceImpl#getAccount calls GET {?}/api/v1/userservice/users/id/{accountId} through RestTemplate with a host computed at run time (user_service_url + "/api/v1/userservice/users/id/" + accountId); not an edge
- ts-preserve-other-service: ts-preserve-other-service/src/main/java/preserveOther/service/PreserveOtherServiceImpl.java:321: preserveOther.service.PreserveOtherServiceImpl#addAssuranceForOrder calls GET {?}/api/v1/assuranceservice/assurances/{assuranceType}/{orderId} through RestTemplate with a host computed at run time (assurance_service_url + "/api/v1/assuranceservice/assurances/" + assuranceType + "/" + orderId); not an edge
- ts-preserve-other-service: ts-preserve-other-service/src/main/java/preserveOther/service/PreserveOtherServiceImpl.java:338: preserveOther.service.PreserveOtherServiceImpl#queryForStationId calls GET {?}/api/v1/stationservice/stations/id/{stationName} through RestTemplate with a host computed at run time (station_service_url + "/api/v1/stationservice/stations/id/" + stationName); not an edge
- ts-preserve-other-service: ts-preserve-other-service/src/main/java/preserveOther/service/PreserveOtherServiceImpl.java:352: preserveOther.service.PreserveOtherServiceImpl#checkSecurity calls GET {?}/api/v1/securityservice/securityConfigs/{accountId} through RestTemplate with a host computed at run time (security_service_url + "/api/v1/securityservice/securityConfigs/" + accountId); not an edge
- ts-preserve-other-service: ts-preserve-other-service/src/main/java/preserveOther/service/PreserveOtherServiceImpl.java:367: preserveOther.service.PreserveOtherServiceImpl#getTripAllDetailInformation calls POST {?}/api/v1/travel2service/trip_detail through RestTemplate with a host computed at run time (travel2_service_url + "/api/v1/travel2service/trip_detail"); not an edge
- ts-preserve-other-service: ts-preserve-other-service/src/main/java/preserveOther/service/PreserveOtherServiceImpl.java:382: preserveOther.service.PreserveOtherServiceImpl#getContactsById calls GET {?}/api/v1/contactservice/contacts/{contactsId} through RestTemplate with a host computed at run time (contacts_service_url + "/api/v1/contactservice/contacts/" + contactsId); not an edge
- ts-preserve-other-service: ts-preserve-other-service/src/main/java/preserveOther/service/PreserveOtherServiceImpl.java:397: preserveOther.service.PreserveOtherServiceImpl#createOrder calls POST {?}/api/v1/orderOtherService/orderOther through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther"); not an edge
- ts-preserve-other-service: ts-preserve-other-service/src/main/java/preserveOther/service/PreserveOtherServiceImpl.java:413: preserveOther.service.PreserveOtherServiceImpl#createFoodOrder calls POST {?}/api/v1/foodservice/orders through RestTemplate with a host computed at run time (food_service_url + "/api/v1/foodservice/orders"); not an edge
- ts-preserve-other-service: ts-preserve-other-service/src/main/java/preserveOther/service/PreserveOtherServiceImpl.java:427: preserveOther.service.PreserveOtherServiceImpl#createConsign calls POST {?}/api/v1/consignservice/consigns through RestTemplate with a host computed at run time (consign_service_url + "/api/v1/consignservice/consigns"); not an edge
- ts-preserve-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-preserve-service: ts-preserve-service/src/main/java/preserve/service/PreserveServiceImpl.java:129: preserve.service.PreserveServiceImpl#preserve calls POST {?}/api/v1/basicservice/basic/travel through RestTemplate with a host computed at run time (basic_service_url + "/api/v1/basicservice/basic/travel"); not an edge
- ts-preserve-service: ts-preserve-service/src/main/java/preserve/service/PreserveServiceImpl.java:278: preserve.service.PreserveServiceImpl#dipatchSeat calls POST {?}/api/v1/seatservice/seats through RestTemplate with a host computed at run time (seat_service_url + "/api/v1/seatservice/seats"); not an edge
- ts-preserve-service: ts-preserve-service/src/main/java/preserve/service/PreserveServiceImpl.java:306: preserve.service.PreserveServiceImpl#getAccount calls GET {?}/api/v1/userservice/users/id/{accountId} through RestTemplate with a host computed at run time (user_service_url + "/api/v1/userservice/users/id/" + accountId); not an edge
- ts-preserve-service: ts-preserve-service/src/main/java/preserve/service/PreserveServiceImpl.java:320: preserve.service.PreserveServiceImpl#addAssuranceForOrder calls GET {?}/api/v1/assuranceservice/assurances/{assuranceType}/{orderId} through RestTemplate with a host computed at run time (assurance_service_url + "/api/v1/assuranceservice/assurances/" + assuranceType + "/" + orderId); not an edge
- ts-preserve-service: ts-preserve-service/src/main/java/preserve/service/PreserveServiceImpl.java:335: preserve.service.PreserveServiceImpl#queryForStationId calls GET {?}/api/v1/stationservice/stations/id/{stationName} through RestTemplate with a host computed at run time (station_service_url + "/api/v1/stationservice/stations/id/" + stationName); not an edge
- ts-preserve-service: ts-preserve-service/src/main/java/preserve/service/PreserveServiceImpl.java:350: preserve.service.PreserveServiceImpl#checkSecurity calls GET {?}/api/v1/securityservice/securityConfigs/{accountId} through RestTemplate with a host computed at run time (security_service_url + "/api/v1/securityservice/securityConfigs/" + accountId); not an edge
- ts-preserve-service: ts-preserve-service/src/main/java/preserve/service/PreserveServiceImpl.java:365: preserve.service.PreserveServiceImpl#getTripAllDetailInformation calls POST {?}/api/v1/travelservice/trip_detail through RestTemplate with a host computed at run time (travel_service_url + "/api/v1/travelservice/trip_detail"); not an edge
- ts-preserve-service: ts-preserve-service/src/main/java/preserve/service/PreserveServiceImpl.java:381: preserve.service.PreserveServiceImpl#getContactsById calls GET {?}/api/v1/contactservice/contacts/{contactsId} through RestTemplate with a host computed at run time (contacts_service_url + "/api/v1/contactservice/contacts/" + contactsId); not an edge
- ts-preserve-service: ts-preserve-service/src/main/java/preserve/service/PreserveServiceImpl.java:396: preserve.service.PreserveServiceImpl#createOrder calls POST {?}/api/v1/orderservice/order through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order"); not an edge
- ts-preserve-service: ts-preserve-service/src/main/java/preserve/service/PreserveServiceImpl.java:411: preserve.service.PreserveServiceImpl#createFoodOrder calls POST {?}/api/v1/foodservice/orders through RestTemplate with a host computed at run time (food_service_url + "/api/v1/foodservice/orders"); not an edge
- ts-preserve-service: ts-preserve-service/src/main/java/preserve/service/PreserveServiceImpl.java:425: preserve.service.PreserveServiceImpl#createConsign calls POST {?}/api/v1/consignservice/consigns through RestTemplate with a host computed at run time (consign_service_url + "/api/v1/consignservice/consigns"); not an edge
- ts-price-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-rebook-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-rebook-service: ts-rebook-service/src/main/java/rebook/service/RebookServiceImpl.java:251: rebook.service.RebookServiceImpl#dipatchSeat calls POST {?}/api/v1/seatservice/seats through RestTemplate with a host computed at run time (seat_service_url + "/api/v1/seatservice/seats"); not an edge
- ts-rebook-service: ts-rebook-service/src/main/java/rebook/service/RebookServiceImpl.java:309: rebook.service.RebookServiceImpl#getTripAllDetailInformation calls POST  through RestTemplate with a host computed at run time (requestUrl); not an edge
- ts-rebook-service: ts-rebook-service/src/main/java/rebook/service/RebookServiceImpl.java:332: rebook.service.RebookServiceImpl#createOrder calls POST  through RestTemplate with a host computed at run time (requestUrl); not an edge
- ts-rebook-service: ts-rebook-service/src/main/java/rebook/service/RebookServiceImpl.java:351: rebook.service.RebookServiceImpl#updateOrder calls PUT  through RestTemplate with a host computed at run time (requestOrderUtl); not an edge
- ts-rebook-service: ts-rebook-service/src/main/java/rebook/service/RebookServiceImpl.java:371: rebook.service.RebookServiceImpl#deleteOrder calls POST  through RestTemplate with a host computed at run time (requestUrl); not an edge
- ts-rebook-service: ts-rebook-service/src/main/java/rebook/service/RebookServiceImpl.java:393: rebook.service.RebookServiceImpl#getOrderByRebookInfo calls GET  through RestTemplate with a host computed at run time (requestUrl); not an edge
- ts-rebook-service: ts-rebook-service/src/main/java/rebook/service/RebookServiceImpl.java:407: rebook.service.RebookServiceImpl#queryTrainTypeByName calls GET {?}/api/v1/trainservice/trains/byName/{trainTypeName} through RestTemplate with a host computed at run time (train_service_url + "/api/v1/trainservice/trains/byName/" + trainTypeName); not an edge
- ts-rebook-service: ts-rebook-service/src/main/java/rebook/service/RebookServiceImpl.java:420: rebook.service.RebookServiceImpl#getRouteByRouteId calls GET {?}/api/v1/routeservice/routes/{routeId} through RestTemplate with a host computed at run time (route_service_url + "/api/v1/routeservice/routes/" + routeId); not an edge
- ts-rebook-service: ts-rebook-service/src/main/java/rebook/service/RebookServiceImpl.java:445: rebook.service.RebookServiceImpl#payDifferentMoney calls POST {?}/api/v1/inside_pay_service/inside_payment/difference through RestTemplate with a host computed at run time (inside_payment_service_url + "/api/v1/inside_pay_service/inside_payment/difference"); not an edge
- ts-rebook-service: ts-rebook-service/src/main/java/rebook/service/RebookServiceImpl.java:459: rebook.service.RebookServiceImpl#drawBackMoney calls GET {?}/api/v1/inside_pay_service/inside_payment/drawback/{userId}/{money} through RestTemplate with a host computed at run time (inside_payment_service_url + "/api/v1/inside_pay_service/inside_payment/drawback/" + userId + "/" + money); not an edge
- ts-route-plan-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-route-plan-service: ts-route-plan-service/src/main/java/plan/service/RoutePlanServiceImpl.java:169: plan.service.RoutePlanServiceImpl#searchMinStopStations calls GET {?}/api/v1/routeservice/routes/{?}/{?} through RestTemplate with a host computed at run time (route_service_url + "/api/v1/routeservice/routes/" + info.getStartStation() + "/" + info.getEndStation()); not an edge
- ts-route-plan-service: ts-route-plan-service/src/main/java/plan/service/RoutePlanServiceImpl.java:205: plan.service.RoutePlanServiceImpl#searchMinStopStations calls POST {?}/api/v1/travelservice/trips/routes through RestTemplate with a host computed at run time (travel_service_url + "/api/v1/travelservice/trips/routes"); not an edge
- ts-route-plan-service: ts-route-plan-service/src/main/java/plan/service/RoutePlanServiceImpl.java:215: plan.service.RoutePlanServiceImpl#searchMinStopStations calls POST {?}/api/v1/travel2service/trips/routes through RestTemplate with a host computed at run time (travel2_service_url + "/api/v1/travel2service/trips/routes"); not an edge
- ts-route-plan-service: ts-route-plan-service/src/main/java/plan/service/RoutePlanServiceImpl.java:253: plan.service.RoutePlanServiceImpl#searchMinStopStations calls POST  through RestTemplate with a host computed at run time (requestUrl); not an edge
- ts-route-plan-service: ts-route-plan-service/src/main/java/plan/service/RoutePlanServiceImpl.java:289: plan.service.RoutePlanServiceImpl#getRouteByRouteId calls GET {?}/api/v1/routeservice/routes/{routeId} through RestTemplate with a host computed at run time (route_service_url + "/api/v1/routeservice/routes/" + routeId); not an edge
- ts-route-plan-service: ts-route-plan-service/src/main/java/plan/service/RoutePlanServiceImpl.java:310: plan.service.RoutePlanServiceImpl#getTripFromHighSpeedTravelServive calls POST {?}/api/v1/travelservice/trips/left through RestTemplate with a host computed at run time (travel_service_url + "/api/v1/travelservice/trips/left"); not an edge
- ts-route-plan-service: ts-route-plan-service/src/main/java/plan/service/RoutePlanServiceImpl.java:325: plan.service.RoutePlanServiceImpl#getTripFromNormalTrainTravelService calls POST {?}/api/v1/travel2service/trips/left through RestTemplate with a host computed at run time (travel2_service_url + "/api/v1/travel2service/trips/left"); not an edge
- ts-route-plan-service: ts-route-plan-service/src/main/java/plan/service/RoutePlanServiceImpl.java:347: plan.service.RoutePlanServiceImpl#getStationList calls GET {path} through RestTemplate with a host computed at run time (path); not an edge
- ts-route-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-seat-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-seat-service: ts-seat-service/src/main/java/seat/service/SeatServiceImpl.java:144: seat.service.SeatServiceImpl#getLeftTicketOfInterval calls POST {?}/api/v1/orderservice/order/tickets through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order/tickets"); not an edge
- ts-seat-service: ts-seat-service/src/main/java/seat/service/SeatServiceImpl.java:160: seat.service.SeatServiceImpl#getLeftTicketOfInterval calls POST {?}/api/v1/orderOtherService/orderOther/tickets through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther/tickets"); not an edge
- ts-seat-service: ts-seat-service/src/main/java/seat/service/SeatServiceImpl.java:210: seat.service.SeatServiceImpl#getDirectProportion calls GET {?}/api/v1/configservice/configs/DirectTicketAllocationProportion through RestTemplate with a host computed at run time (config_service_url + "/api/v1/configservice/configs/" + configName); not an edge
- ts-seat-service: ts-seat-service/src/main/java/seat/service/SeatServiceImpl.java:60: seat.service.SeatServiceImpl#distributeSeat calls POST {?}/api/v1/orderservice/order/tickets through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order/tickets"); not an edge
- ts-seat-service: ts-seat-service/src/main/java/seat/service/SeatServiceImpl.java:73: seat.service.SeatServiceImpl#distributeSeat calls POST {?}/api/v1/orderOtherService/orderOther/tickets through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther/tickets"); not an edge
- ts-security-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-security-service: ts-security-service/src/main/java/security/service/SecurityServiceImpl.java:133: security.service.SecurityServiceImpl#getSecurityOrderInfoFromOrder calls GET {?}/api/v1/orderservice/order/security/{checkDate}/{accountId} through RestTemplate with a host computed at run time (order_service_url + "/api/v1/orderservice/order/security/" + checkDate + "/" + accountId); not an edge
- ts-security-service: ts-security-service/src/main/java/security/service/SecurityServiceImpl.java:148: security.service.SecurityServiceImpl#getSecurityOrderOtherInfoFromOrder calls GET {?}/api/v1/orderOtherService/orderOther/security/{checkDate}/{accountId} through RestTemplate with a host computed at run time (order_other_service_url + "/api/v1/orderOtherService/orderOther/security/" + checkDate + "/" + accountId); not an edge
- ts-station-food-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-station-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-train-food-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-train-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-travel-plan-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-travel-plan-service: ts-travel-plan-service/src/main/java/travelplan/service/TravelPlanServiceImpl.java:244: travelplan.service.TravelPlanServiceImpl#getRestTicketNumber calls POST {?}/api/v1/seatservice/seats/left_tickets through RestTemplate with a host computed at run time (seat_service_url + "/api/v1/seatservice/seats/left_tickets"); not an edge
- ts-travel-plan-service: ts-travel-plan-service/src/main/java/travelplan/service/TravelPlanServiceImpl.java:257: travelplan.service.TravelPlanServiceImpl#getRoutePlanResultCheapest calls POST {?}/api/v1/routeplanservice/routePlan/cheapestRoute through RestTemplate with a host computed at run time (route_plan_service_url + "/api/v1/routeplanservice/routePlan/cheapestRoute"); not an edge
- ts-travel-plan-service: ts-travel-plan-service/src/main/java/travelplan/service/TravelPlanServiceImpl.java:269: travelplan.service.TravelPlanServiceImpl#getRoutePlanResultQuickest calls POST {?}/api/v1/routeplanservice/routePlan/quickestRoute through RestTemplate with a host computed at run time (route_plan_service_url + "/api/v1/routeplanservice/routePlan/quickestRoute"); not an edge
- ts-travel-plan-service: ts-travel-plan-service/src/main/java/travelplan/service/TravelPlanServiceImpl.java:282: travelplan.service.TravelPlanServiceImpl#getRoutePlanResultMinStation calls POST {?}/api/v1/routeplanservice/routePlan/minStopStations through RestTemplate with a host computed at run time (route_plan_service_url + "/api/v1/routeplanservice/routePlan/minStopStations"); not an edge
- ts-travel-plan-service: ts-travel-plan-service/src/main/java/travelplan/service/TravelPlanServiceImpl.java:294: travelplan.service.TravelPlanServiceImpl#tripsFromHighSpeed calls POST {?}/api/v1/travelservice/trips/left through RestTemplate with a host computed at run time (travel_service_url + "/api/v1/travelservice/trips/left"); not an edge
- ts-travel-plan-service: ts-travel-plan-service/src/main/java/travelplan/service/TravelPlanServiceImpl.java:307: travelplan.service.TravelPlanServiceImpl#tripsFromNormal calls POST {?}/api/v1/travel2service/trips/left through RestTemplate with a host computed at run time (travel2_service_url + "/api/v1/travel2service/trips/left"); not an edge
- ts-travel-plan-service: ts-travel-plan-service/src/main/java/travelplan/service/TravelPlanServiceImpl.java:320: travelplan.service.TravelPlanServiceImpl#queryTrainTypeByName calls GET {?}/api/v1/trainservice/trains/byName/{trainTypeName} through RestTemplate with a host computed at run time (train_service_url + "/api/v1/trainservice/trains/byName/" + trainTypeName); not an edge
- ts-travel-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-travel-service: ts-travel-service/src/main/java/travel/service/TravelServiceImpl.java:347: travel.service.TravelServiceImpl#getTicketsByBatch calls POST {?}/api/v1/basicservice/basic/travels through RestTemplate with a host computed at run time (basic_service_url + "/api/v1/basicservice/basic/travels"); not an edge
- ts-travel-service: ts-travel-service/src/main/java/travel/service/TravelServiceImpl.java:396: travel.service.TravelServiceImpl#getTickets calls POST {?}/api/v1/basicservice/basic/travel through RestTemplate with a host computed at run time (basic_service_url + "/api/v1/basicservice/basic/travel"); not an edge
- ts-travel-service: ts-travel-service/src/main/java/travel/service/TravelServiceImpl.java:507: travel.service.TravelServiceImpl#getTrainTypeByName calls GET {?}/api/v1/trainservice/trains/byName/{trainTypeName} through RestTemplate with a host computed at run time (train_service_url + "/api/v1/trainservice/trains/byName/" + trainTypeName); not an edge
- ts-travel-service: ts-travel-service/src/main/java/travel/service/TravelServiceImpl.java:521: travel.service.TravelServiceImpl#getRouteByRouteId calls GET {?}/api/v1/routeservice/routes/{routeId} through RestTemplate with a host computed at run time (route_service_url + "/api/v1/routeservice/routes/" + routeId); not an edge
- ts-travel-service: ts-travel-service/src/main/java/travel/service/TravelServiceImpl.java:552: travel.service.TravelServiceImpl#getRestTicketNumber calls POST {?}/api/v1/seatservice/seats/left_tickets through RestTemplate with a host computed at run time (seat_service_url + "/api/v1/seatservice/seats/left_tickets"); not an edge
- ts-travel2-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-travel2-service: ts-travel2-service/src/main/java/travel2/service/TravelServiceImpl.java:264: travel2.service.TravelServiceImpl#getTicketsByBatch calls POST {?}/api/v1/basicservice/basic/travels through RestTemplate with a host computed at run time (basic_service_url + "/api/v1/basicservice/basic/travels"); not an edge
- ts-travel2-service: ts-travel2-service/src/main/java/travel2/service/TravelServiceImpl.java:312: travel2.service.TravelServiceImpl#getTickets calls POST {?}/api/v1/basicservice/basic/travel through RestTemplate with a host computed at run time (basic_service_url + "/api/v1/basicservice/basic/travel"); not an edge
- ts-travel2-service: ts-travel2-service/src/main/java/travel2/service/TravelServiceImpl.java:418: travel2.service.TravelServiceImpl#getTrainTypeByName calls GET {?}/api/v1/trainservice/trains/byName/{trainTypeName} through RestTemplate with a host computed at run time (train_service_url + "/api/v1/trainservice/trains/byName/" + trainTypeName); not an edge
- ts-travel2-service: ts-travel2-service/src/main/java/travel2/service/TravelServiceImpl.java:432: travel2.service.TravelServiceImpl#getRouteByRouteId calls GET {?}/api/v1/routeservice/routes/{routeId} through RestTemplate with a host computed at run time (route_service_url + "/api/v1/routeservice/routes/" + routeId); not an edge
- ts-travel2-service: ts-travel2-service/src/main/java/travel2/service/TravelServiceImpl.java:463: travel2.service.TravelServiceImpl#getRestTicketNumber calls POST {?}/api/v1/seatservice/seats/left_tickets through RestTemplate with a host computed at run time (seat_service_url + "/api/v1/seatservice/seats/left_tickets"); not an edge
- ts-user-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-user-service: ts-user-service/src/main/java/user/service/impl/UserServiceImpl.java:183: user.service.impl.UserServiceImpl#deleteUserAuth calls DELETE {?}/api/v1/users/{userId} through RestTemplate with a host computed at run time (AUTH_SERVICE_URI + "/users/" + userId); not an edge
- ts-user-service: ts-user-service/src/main/java/user/service/impl/UserServiceImpl.java:94: user.service.impl.UserServiceImpl#createDefaultAuthUser calls POST {?}/api/v1/auth through RestTemplate with a host computed at run time (auth_service_url + "/api/v1/auth"); not an edge
- ts-verification-code-service: no VirtualService; assumed to be in the mesh, so the Istio default retry policy applies
- ts-voucher-service: HTTP routes at deployment/fault-inject-deployment/virtual-services-fault.yaml:9, deployment/fault-inject-deployment/virtual-services-fault.yaml:14 are not applied; no collected edge calls ts-voucher-service
- ts-wait-order-service: ts-wait-order-service/src/main/java/waitorder/utils/PollThread.java:75: waitorder.utils.PollThread#doPreserve calls POST {url}/api/v1/contactservice/preserve through RestTemplate with a host computed at run time (url + "/api/v1/contactservice/preserve"); not an edge
