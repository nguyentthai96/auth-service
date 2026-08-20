package com.ntt.sysadminservice.apipartner.adapter.`in`.web

import com.ntt.sysadminservice.apipartner.application.ApiPartnerService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

/**
 * API Usage controller — usage dashboard & IP whitelist management (FR-012).
 */
@RestController
@RequestMapping("/api/admin")
class ApiUsageController(
    private val apiPartnerService: ApiPartnerService
) {

    @GetMapping("/api-usage")
    fun getUsageStatistics(@RequestParam partnerId: Long): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(apiPartnerService.getUsageStatistics(partnerId))
    }

    @PutMapping("/api-partners/{id}/ip-whitelist")
    fun updateIpWhitelist(
        @PathVariable id: Long,
        @RequestBody request: IpWhitelistRequest
    ): ResponseEntity<Map<String, Boolean>> {
        apiPartnerService.updateIpWhitelist(id, request.ipAddresses)
        return ResponseEntity.ok(mapOf("success" to true))
    }
}

data class IpWhitelistRequest(
    val ipAddresses: List<String>
)
