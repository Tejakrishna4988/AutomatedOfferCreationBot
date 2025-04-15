import React, { useState } from 'react';
import { 
  Box, 
  Button, 
  TextField, 
  Typography, 
  Paper, 
  CircularProgress,
  Alert,
  IconButton
} from '@mui/material';
import { Upload as UploadIcon, Download as DownloadIcon } from '@mui/icons-material';
import { extractFromText, extractFromFile } from '../../services/api';
import './styles.css';

const OfferCreationAI = () => {
  const [input, setInput] = useState('');
  const [file, setFile] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [result, setResult] = useState(null);

  const handleFileChange = (event) => {
    const selectedFile = event.target.files[0];
    setFile(selectedFile);
    setInput(''); // Clear text input when file is selected
  };

  const handleTextChange = (event) => {
    setInput(event.target.value);
    setFile(null); // Clear file when text is entered
  };

  const isExcelOrCsv = (file) => {
    const validExtensions = ['.csv', '.xlsx', '.xls'];
    const fileName = file.name.toLowerCase();
    return validExtensions.some(ext => fileName.endsWith(ext));
  };

  const processInput = async () => {
    setLoading(true);
    setError(null);
    setResult(null);

    try {
      if (file) {
        if (isExcelOrCsv(file)) {
          const response = await extractFromFile(file);
          setResult(response);
        } else {
          setError('Please upload a CSV or Excel file');
        }
      } else if (input.trim()) {
        const response = await extractFromText(input);
        setResult(response);
      } else {
        setError('Please enter text or upload a file');
      }
    } catch (err) {
      setError(err.message || 'An error occurred while processing your request');
    } finally {
      setLoading(false);
    }
  };

  const downloadExcel = () => {
    if (result?.excel) {
      const link = document.createElement('a');
      link.href = `data:application/vnd.openxmlformats-officedocument.spreadsheetml.sheet;base64,${result.excel}`;
      link.download = 'extracted_offers.xlsx';
      link.click();
    }
  };

  return (
    <Box className="offer-creation-container">
      <Typography variant="h4" gutterBottom>
        Create Offer with AI
      </Typography>

      <Paper elevation={3} className="input-section">
        <Box className="text-input">
          <TextField
            fullWidth
            multiline
            rows={5}
            value={input}
            onChange={handleTextChange}
            placeholder="Enter offer text here..."
            disabled={loading || !!file}
            variant="outlined"
          />
        </Box>

        <Box className="file-input">
          <Button
            variant="contained"
            component="label"
            startIcon={<UploadIcon />}
            disabled={loading || !!input}
          >
            Upload File
            <input
              type="file"
              hidden
              onChange={handleFileChange}
              accept=".csv,.xlsx,.xls"
            />
          </Button>
          {file && (
            <Typography variant="body2" className="file-name">
              Selected file: {file.name}
            </Typography>
          )}
        </Box>
      </Paper>

      <Button
        variant="contained"
        color="primary"
        onClick={processInput}
        disabled={loading || (!input.trim() && !file)}
        className="create-button"
      >
        {loading ? <CircularProgress size={24} /> : 'Create Offer'}
      </Button>

      {error && (
        <Alert severity="error" className="error">
          {error}
        </Alert>
      )}

      {result && (
        <Paper elevation={3} className="result">
          <Typography variant="h6" gutterBottom>
            Extracted Offer
          </Typography>
          
          <pre>{JSON.stringify(result, null, 2)}</pre>
          
          {result.excel && (
            <Button
              variant="contained"
              color="secondary"
              startIcon={<DownloadIcon />}
              onClick={downloadExcel}
              className="download-button"
            >
              Download Excel
            </Button>
          )}
        </Paper>
      )}
    </Box>
  );
};

export default OfferCreationAI; 