import React, { useState } from "react";
import "./styles.css";
import "./uploadForm.css";
interface OfferData {
  brand: string;
  offerType: string;
  offerStartDate: string;
  offerEndDate: string;
  offerDescription: string;
  orgAcquisitionType: string;
  velocityCheckType: string;
  commonVelocityEnabled: boolean;
  velocityCheckApplied: string;
  velocityCheckCount: number;
  priority: number;
  offerCode: string;
}

interface SkuData {
  sku_code: string;
  min_amount: string;
  max_amount: string;
  include_states: string;
  exclude_states: string;
  bank_name: string;
  card_type: string;
  full_swipe_offer_amount_type: string;
  full_swipe_offer_value: string;
  full_swipe_offer_max_amount: string;
  emi_offer_amount_type: string;
  emi_offer_value: string;
  emi_offer_max_amount: string;
  full_swipe_subvention_type: string;
  full_swipe_bank_subvention_value: string;
  full_swipe_brand_subvention_value: string;
  emi_subvention_type: string;
  emi_bank_subvention_value: string;
  emi_brand_subvention_value: string;
  start_date: string;
  end_date: string;
}

const OfferCreationAI: React.FC = () => {
  const [step, setStep] = useState<number>(1);
  const [formData, setFormData] = useState<OfferData>({
    brand: "",
    offerType: "",
    offerStartDate: "",
    offerEndDate: "",
    offerDescription: "",
    orgAcquisitionType: "",
    velocityCheckType: "",
    commonVelocityEnabled: false,
    velocityCheckApplied: "Per Transaction",
    velocityCheckCount: 1,
    priority: 1,
    offerCode: "",
  });

  const [skuData, setSkuData] = useState<SkuData | null>(null);
  const [rawText, setRawText] = useState("");
  const [error, setError] = useState("");
  const [file, setFile] = useState<File | null>(null);
  const [loading, setLoading] = useState(false);

  const handleFileChange = (event: React.ChangeEvent<HTMLInputElement>) => {
    if (event.target.files && event.target.files[0]) {
      setFile(event.target.files[0]);
    }
  };

  const transformSkuDataToOfferData = (data: SkuData): OfferData => {
    const brandMatch = data.sku_code.match(/^([A-Za-z]+)/);
    const brand = brandMatch ? brandMatch[1].toUpperCase() : "";

    return {
      brand,
      offerType: "Additional Cashback",
      offerStartDate: data.start_date.split(" ")[0],
      offerEndDate: data.end_date.split(" ")[0],
      offerDescription: `${data.full_swipe_offer_amount_type} offer of ${data.full_swipe_offer_value} for ${data.bank_name} ${data.card_type} cards`,
      orgAcquisitionType: "Direct",
      velocityCheckType: "PERDAY",
      commonVelocityEnabled: true,
      velocityCheckApplied: "Per Transaction",
      velocityCheckCount: 1,
      priority: 1,
      offerCode: `${brand}_${data.bank_name}_${data.start_date.split(" ")[0]}`,
    };
  };

  const handleTextSubmit = async () => {
    if (!rawText.trim()) {
      setError("Please enter some text first");
      return;
    }

    try {
      setLoading(true);
      setError("");

      const [jsonResponse, excelResponse] = await Promise.all([
        fetch("http://localhost:8080/api/offer/extractText", {
          method: "POST",
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({ text: rawText }),
        }),
        fetch("http://localhost:8080/api/offer/extract-from-text", {
          method: "POST",
          headers: {
            "Content-Type": "text/plain",
          },
          body: rawText,
        }),
      ]);

      if (!jsonResponse.ok || !excelResponse.ok) {
        throw new Error("Failed to extract offer details");
      }

      const data = await jsonResponse.json();

      if ("sku_code" in data) {
        setSkuData(data);
        setFormData(transformSkuDataToOfferData(data));
      } else {
        setFormData(data);
        setSkuData(null);
      }

      // Handle Excel download
      const blob = await excelResponse.blob();
      const url = window.URL.createObjectURL(
        new Blob([blob], {
          type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        })
      );
      const a = document.createElement("a");
      a.href = url;
      a.download = "extracted_offers.xlsx";
      document.body.appendChild(a);
      a.click();
      window.URL.revokeObjectURL(url);
      document.body.removeChild(a);

      // Move to the next step
      setStep(3);
    } catch (err) {
      setError(err instanceof Error ? err.message : "An error occurred");
    } finally {
      setLoading(false);
    }
  };

  const handleFileSubmit = async () => {
    if (!file) {
      setError("Please select a file first");
      return;
    }

    if (file.size === 0) {
      setError("The selected file is empty");
      return;
    }

    try {
      setLoading(true);
      setError("");

      const formData = new FormData();
      formData.append("file", file);

      const [jsonResponse, csvResponse] = await Promise.all([
        fetch("http://localhost:8080/api/offer/extract-json", {
          method: "POST",
          body: formData,
        }),
        fetch("http://localhost:8080/api/csv/process", {
          method: "POST",
          body: formData,
        }),
      ]);

      if (!jsonResponse.ok) {
        throw new Error("Failed to extract offer details");
      }

      const data = await jsonResponse.json();
      setFormData(data);

      if (csvResponse.ok) {
        const arrayBuffer = await csvResponse.arrayBuffer();
        const blob = new Blob([arrayBuffer], {
          type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        });
        const url = window.URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = "extracted_offers.xlsx";
        document.body.appendChild(a);
        a.click();
        window.URL.revokeObjectURL(url);
        document.body.removeChild(a);
      }

      // Move to the next step
      setStep(3);
    } catch (err) {
      setError(err instanceof Error ? err.message : "An error occurred");
    } finally {
      setLoading(false);
    }
  };

  const renderStep1 = () => (
    <div className="welcome-screen">
      <h1>Create Offer with AI</h1>
      <p>
        Welcome to the AI-powered offer creation system. Let's help you create
        your offer quickly and efficiently.
      </p>
      <button
        onClick={() => setStep(2)}
        className="primary-button"
        style={{ margin: "auto" }}
      >
        Get Started
      </button>
    </div>
  );

  const renderStep2 = () => (
    <div className="input-section-render2">
      <h2 className="heading-render2">Upload Offer Details</h2>

      <div className="text-input-render2">
        <textarea
          value={rawText}
          onChange={(e) => setRawText(e.target.value)}
          placeholder="Please Enter Offer Details Here"
          className="textarea-render2"
        />
        <button
          onClick={handleTextSubmit}
          id="text-submit"
          className="button-render2"
          disabled={loading}
        >
          Extract from Text
        </button>
      </div>

      <div className="file-input-render2">
        <label htmlFor="file-upload" className="file-upload-label-render2">
          <span className="upload-icon-render2">📁</span>
          <span className="upload-text-render2">
            {!file ?" Click to Upload Offer File (Excel/CSV)" : file.name}
          </span>
        </label>
        <input
          type="file"
          accept=".xlsx,.xls,.csv"
          onChange={handleFileChange}
          id="file-upload"
          className="file-upload-render2"
        />
        <button
          onClick={handleFileSubmit}
          id="file-submit"
          className="button-render2"
          disabled={loading}
        >
          Extract from File
        </button>
      </div>
    </div>
  );

  const renderStep3 = () => (
    <div className="form-preview">
      <h2>Generated Offer Details</h2>
      <div className="form-fields">
        {skuData ? (
          // Render SKU data fields
          <div className="sku-data">
            <h3>SKU Details</h3>
            {Object.entries(skuData).map(([key, value]) => (
              <div key={key} className="form-field">
                <label>{key.replace(/_/g, " ").toUpperCase()}</label>
                <input type="text" value={value} readOnly />
              </div>
            ))}
          </div>
        ) : (
          // Render regular offer data fields
          <div className="offer-data">
            <h3>Offer Details</h3>
            {Object.entries(formData).map(([key, value]) => (
              <div key={key} className="form-field">
                <label>{key.replace(/([A-Z])/g, " $1").toUpperCase()}</label>
                <input
                  type={typeof value === "boolean" ? "checkbox" : "text"}
                  checked={typeof value === "boolean" ? value : undefined}
                  value={typeof value !== "boolean" ? value : undefined}
                  readOnly
                />
              </div>
            ))}
          </div>
        )}
      </div>
      <div
        className="action-buttons"
        style={{
          display: "flex",
          flexDirection: "column",
          gap: "10px",
          alignItems: "center",
        }}
      >
        <div
          className="file-input"
          style={{
            display: "flex",
            flexDirection: "column",
            alignItems: "center",
            width: "100%",
            marginBottom: "20px",
          }}
        >
          <input
            type="file"
            accept=".xlsx,.xls,.csv"
            onChange={handleFileChange}
            id="offer-upload"
            style={{ width: "80%", marginBottom: "20px" }}
          />
          <label htmlFor="offer-upload" className="file-upload-label">
            Upload Offer Sheet
          </label>
          {file && <div className="file-name">Selected: {file.name}</div>}
        </div>
        <button
          onClick={() => setStep(2)}
          className="secondary-button"
          style={{ width: "200px" }}
        >
          Back to Upload
        </button>
        <button
          onClick={() => setStep(4)}
          className="primary-button"
          style={{ width: "200px" }}
        >
          Submit Details
        </button>
      </div>
    </div>
  );

  const renderStep4 = () => (
    <div
      className="success-screen"
      style={{
        textAlign: "center",
        padding: "40px",
        backgroundColor: "black",
        borderRadius: "8px",
        color: "white",
      }}
    >
      <h2 style={{ fontSize: "2em", marginBottom: "20px" }}>Success!</h2>
      <p style={{ fontSize: "1.2em", marginBottom: "30px" }}>
        Your offer has been successfully created and submitted.
      </p>
      <div style={{ marginBottom: "20px" }}>
        <svg
          width="100"
          height="100"
          viewBox="0 0 24 24"
          fill="none"
          xmlns="http://www.w3.org/2000/svg"
        >
          <path
            d="M9 16.17L4.83 12l-1.42 1.41L9 19 21 7l-1.41-1.41L9 16.17z"
            fill="green"
          />
        </svg>
        <p>Offer_abchsjjs123400</p>
      </div>
      <button
        onClick={() => window.location.reload()}
        className="primary-button"
        style={{
          padding: "10px 20px",
          fontSize: "1.1em",
          backgroundColor: "#4CAF50",
          color: "white",
          border: "none",
          borderRadius: "4px",
          cursor: "pointer",
        }}
      >
        Create Another Offer
      </button>
    </div>
  );

  return (
    <div className="offer-creation-container">
      {error && <div className="error">{error}</div>}
      {step === 1 && renderStep1()}
      {step === 2 && renderStep2()}
      {step === 3 && renderStep3()}
      {step === 4 && renderStep4()}
    </div>
  );
};

export default OfferCreationAI;
