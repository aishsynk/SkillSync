/**
 * Project-Wide Notification Helper
 * Wraps Color Admin Gritter (toasts) and SweetAlert (confirmations) with safe fallbacks.
 */

var notificationHelper = (function () {
    // Check if libraries are loaded
    var hasGritter = typeof $.gritter !== 'undefined';
    var hasSweetAlert = typeof swal !== 'undefined';

    function showGritter(title, message, className) {
        if (hasGritter) {
            $.gritter.add({
                title: title,
                text: message,
                class_name: className
            });
        } else {
            // Fallback to basic browser alert if assets are missing
            alert(title + "\n\n" + message);
        }
    }

    return {
        showSuccess: function (message, title) {
            showGritter(title || "Success", message, "gritter-success");
        },
        showError: function (message, title) {
            showGritter(title || "Error", message, "gritter-danger");
        },
        showWarning: function (message, title) {
            showGritter(title || "Warning", message, "gritter-warning");
        },
        showInfo: function (message, title) {
            showGritter(title || "Information", message, "gritter-info");
        },

        /**
         * options: {
         *   title: string,
         *   text: string,
         *   icon: 'warning' | 'error' | 'success' | 'info',
         *   buttons: { cancel: true, confirm: { text: "Yes", className: "btn-danger" } },
         *   dangerMode: boolean,
         *   onConfirm: function()
         * }
         */
        confirmAction: function (options) {
            if (hasSweetAlert) {
                swal({
                    title: options.title || "Are you sure?",
                    text: options.text || "This action cannot be undone.",
                    icon: options.icon || "warning",
                    buttons: options.buttons || {
                        cancel: {
                            text: "Cancel",
                            value: null,
                            visible: true,
                            className: "btn btn-default",
                            closeModal: true,
                        },
                        confirm: {
                            text: "Confirm",
                            value: true,
                            visible: true,
                            className: options.dangerMode ? "btn btn-danger" : "btn btn-primary",
                            closeModal: true
                        }
                    },
                    dangerMode: options.dangerMode !== undefined ? options.dangerMode : true,
                }).then(function (confirmed) {
                    if (confirmed && typeof options.onConfirm === "function") {
                        options.onConfirm();
                    }
                });
            } else {
                // Fallback to browser confirm
                if (confirm(options.title + "\n\n" + options.text)) {
                    if (typeof options.onConfirm === "function") {
                        options.onConfirm();
                    }
                }
            }
        },

        /**
         * Handle standard JSON AJAX response
         */
        showAjaxResult: function (response) {
            if (response && response.success !== undefined) {
                if (response.success) {
                    this.showSuccess(response.message || "Operation successful.");
                } else {
                    this.showError(response.message || "Operation failed.");
                }
            }
        }
    };
})();
