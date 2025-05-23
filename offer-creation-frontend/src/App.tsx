import React from 'react';
import { Routes, Route, useNavigate } from 'react-router-dom';
import { Button, Container, Typography, Box } from '@mui/material';
import OfferCreationAI from './components/OfferCreationAI';
import './App.css';

const Home: React.FC = () => {
  const navigate = useNavigate();

  return (
    <Container maxWidth="sm" sx={{ backgroundColor: 'white', minHeight: '100vh', padding: '20px' }}>
      <Box
        sx={{
          display: 'flex',
          flexDirection: 'column',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '100vh',
          gap: 2
        }}
      >
        <Typography variant="h4" component="h1" gutterBottom sx={{ color: 'black' }}>
          Automatic Offer Creation AI
        </Typography>
        <Button
          variant="contained"
          color="primary"
          size="large"
          onClick={() => navigate('/create-offer')}
          sx={{ 
            backgroundColor: 'black',
            color: 'white',
            '&:hover': {
              backgroundColor: '#333333'
            }
          }}
        >
          Create Offer with AI
        </Button>
      </Box>
    </Container>
  );
};

function App() {
  return (
    <Routes>
      <Route path="/" element={<Home />} />
      <Route path="/create-offer" element={<OfferCreationAI />} />
    </Routes>
  );
}

export default App;
