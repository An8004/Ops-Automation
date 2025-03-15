package automator;

public class Queries {
    public static final String LOAN_APP_NO =
            "SELECT loan_application_no FROM loan_application WHERE id = ?";

    public static final String REVIEW_STATUS_QUERY =
            "SELECT user_data_review_status FROM loan_application WHERE id = ?";

    public static final String UPDATE_VKYC_INFO_QUERY =
            "UPDATE vkyc_info SET date_created = ? WHERE unique_id_ref = ?";

    public static final String FETCH_VKYC_INFO_QUERY =
            "SELECT status, provider, flow_type, attempts FROM vkyc_info WHERE unique_id_ref = ?";

    public static final String CHECK_CALLING_SERVICE_LEADS_QUERY =
            "SELECT entity_id FROM calling_service_leads WHERE entity_id = " +
                    "(SELECT loan_application_no FROM loan_application WHERE id = ?) AND campaign_id = 'VKYC_NOTRY'";

    public static final String VERIFY_CALLING_SERVICE_LEADS_QUERY =
            "SELECT entity_id, campaign_id, status FROM calling_service_leads WHERE entity_id = " +
                    "(SELECT loan_application_no FROM loan_application WHERE id = ?) AND campaign_id = 'VKYC_NOTRY'";

    public static final String VERIFY_VENDOR_LEAD_DETAILS_QUERY =
            "SELECT entity_id, campaign_id, status FROM vendor_lead_details WHERE entity_id = ? " +
                    "AND status = 'READY_TO_ADD'";
    public static final String VERIFY_VENDOR_LEAD_STATUS_QUERY =
"SELECT entity_id, campaign_id, status FROM vendor_lead_details WHERE entity_id = ? " +"AND status = 'ADDED'";
}

